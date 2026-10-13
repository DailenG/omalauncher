#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Grid {
    pub glyphs: Vec<u32>,
    pub colors: Vec<u32>,
}

pub fn parse_frame(bytes: &[u8], cols: u16, rows: u16) -> Grid {
    let cells = usize::from(cols) * usize::from(rows);
    let mut grid = Grid {
        glyphs: vec![0x20; cells],
        colors: vec![0; cells],
    };
    parse_frame_into(bytes, cols, rows, &mut grid.glyphs, &mut grid.colors);
    grid
}

pub fn parse_frame_into(bytes: &[u8], cols: u16, rows: u16, glyphs: &mut [u32], colors: &mut [u32]) {
    glyphs.fill(0x20);
    colors.fill(0);

    let cols = usize::from(cols);
    let rows = usize::from(rows);
    let mut row = 0;
    let mut col = 0;
    let mut color = 0;
    let mut offset = 0;

    while offset < bytes.len() {
        match bytes[offset] {
            b'\n' => {
                row += 1;
                col = 0;
                offset += 1;
            }
            0x1b => skip_escape(bytes, &mut offset, &mut color),
            _ => {
                let (codepoint, width) = next_codepoint(bytes, offset);
                if row < rows && col < cols {
                    let index = row * cols + col;
                    if index < glyphs.len() && index < colors.len() {
                        glyphs[index] = codepoint;
                        colors[index] = color;
                    }
                }
                col += 1;
                offset += width;
            }
        }
    }
}

fn next_codepoint(bytes: &[u8], offset: usize) -> (u32, usize) {
    let width = match bytes[offset] {
        0x00..=0x7f => 1,
        0xc2..=0xdf => 2,
        0xe0..=0xef => 3,
        0xf0..=0xf4 => 4,
        _ => return (0xfffd, 1),
    };
    let end = offset + width;
    match bytes.get(offset..end).and_then(|sequence| std::str::from_utf8(sequence).ok()) {
        Some(text) => (text.chars().next().unwrap_or('\u{fffd}') as u32, width),
        None => (0xfffd, 1),
    }
}

fn skip_escape(bytes: &[u8], offset: &mut usize, color: &mut u32) {
    *offset += 1;
    let Some(&introducer) = bytes.get(*offset) else {
        return;
    };

    if introducer == b'[' {
        *offset += 1;
        let start = *offset;
        while let Some(&byte) = bytes.get(*offset) {
            *offset += 1;
            if (0x40..=0x7e).contains(&byte) {
                if byte == b'm' {
                    apply_sgr(&bytes[start..*offset - 1], color);
                }
                break;
            }
        }
    } else if matches!(introducer, b']' | b'P' | b'^' | b'_') {
        *offset += 1;
        while let Some(&byte) = bytes.get(*offset) {
            if byte == 0x07 {
                *offset += 1;
                break;
            }
            if byte == 0x1b && bytes.get(*offset + 1) == Some(&b'\\') {
                *offset += 2;
                break;
            }
            *offset += 1;
        }
    } else {
        *offset += 1;
        while let Some(&byte) = bytes.get(*offset) {
            *offset += 1;
            if (0x30..=0x7e).contains(&byte) {
                break;
            }
        }
    }
}

fn apply_sgr(parameters: &[u8], color: &mut u32) {
    let Ok(parameters) = std::str::from_utf8(parameters) else {
        return;
    };
    let values: Vec<_> = parameters.split(';').map(|value| value.parse::<u16>().ok()).collect();
    let mut index = 0;
    while index < values.len() {
        match values[index] {
            Some(0) => {
                *color = 0;
                index += 1;
            }
            Some(38) if values.get(index + 1) == Some(&Some(2)) && index + 4 < values.len() => {
                if let (Some(red), Some(green), Some(blue)) =
                    (values[index + 2], values[index + 3], values[index + 4])
                {
                    if red <= 255 && green <= 255 && blue <= 255 {
                        *color = 0xff00_0000 | (u32::from(red) << 16) | (u32::from(green) << 8) | u32::from(blue);
                    }
                }
                index += 5;
            }
            _ => index += 1,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::parse_frame;

    #[test]
    fn parses_truecolor_and_reset() {
        let grid = parse_frame(b"a\x1b[38;2;1;2;3mb\x1b[0mc", 3, 1);
        assert_eq!(grid.glyphs, vec![u32::from('a'), u32::from('b'), u32::from('c')]);
        assert_eq!(grid.colors, vec![0, 0xff01_0203, 0]);
    }

    #[test]
    fn skips_unknown_csi_sequences() {
        let grid = parse_frame(b"a\x1b[2Jb", 2, 1);
        assert_eq!(grid.glyphs, vec![u32::from('a'), u32::from('b')]);
    }

    #[test]
    fn truncates_long_rows() {
        let grid = parse_frame(b"abcd", 3, 1);
        assert_eq!(grid.glyphs, vec![u32::from('a'), u32::from('b'), u32::from('c')]);
    }

    #[test]
    fn pads_short_rows_and_missing_rows() {
        let grid = parse_frame(b"a\nb", 3, 2);
        assert_eq!(grid.glyphs, vec![u32::from('a'), 0x20, 0x20, u32::from('b'), 0x20, 0x20]);
        assert_eq!(grid.colors, vec![0; 6]);
    }
}

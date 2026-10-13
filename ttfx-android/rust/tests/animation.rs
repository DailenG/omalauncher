use ttfx_android::anim::{effect_names, Animation};

const LOGO: &str = include_str!("fixtures/omarchy-logo.txt");

#[test]
fn every_effect_builds_and_finishes() {
    let mut failures = Vec::new();
    for name in effect_names() {
        let mut animation = match Animation::new(LOGO, &[name.clone()], 90, 24, Some(7)) {
            Ok(animation) => animation,
            Err(error) => {
                failures.push(format!("{name}: {error}"));
                continue;
            }
        };
        if !(0..30_000).any(|_| !animation.next_frame()) {
            failures.push(format!("{name}: did not finish within 30000 frames"));
        }
    }
    assert!(failures.is_empty(), "effects that did not build or finish: {}", failures.join(", "));
}

#[test]
fn equal_seeds_produce_equal_frames() {
    let args = vec!["--random-effect".to_owned()];
    let mut first = Animation::new(LOGO, &args, 90, 24, Some(1234)).unwrap();
    let mut second = Animation::new(LOGO, &args, 90, 24, Some(1234)).unwrap();
    assert_eq!(first.effect(), second.effect());
    for _ in 0..50 {
        assert_eq!(first.next_frame(), second.next_frame());
        assert_eq!(first.glyphs, second.glyphs);
        assert_eq!(first.colors, second.colors);
    }
}

#[test]
fn print_finishes_with_the_second_logo_line() {
    let second_line = LOGO.lines().nth(1).expect("logo has a second line");
    let mut animation = Animation::new(LOGO, &["print".to_owned()], 90, 24, Some(1)).unwrap();
    let mut finished = false;
    for _ in 0..30_000 {
        if !animation.next_frame() {
            finished = true;
            break;
        }
    }
    assert!(finished, "print did not finish");
    let last_frame: Vec<String> = animation
        .glyphs
        .chunks(90)
        .map(|row| row.iter().map(|glyph| char::from_u32(*glyph).unwrap_or('\u{fffd}')).collect())
        .collect();
    assert!(
        last_frame.iter().any(|row| row.contains(second_line)),
        "print's last frame did not contain the logo's second line"
    );
}

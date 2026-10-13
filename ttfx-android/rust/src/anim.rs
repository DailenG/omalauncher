use std::io::IoSlice;

use clap::{CommandFactory, Parser};
use ttfx::cli::Cli;
use ttfx::engine::ctx::Clock;
use ttfx::fx::{effects, run::Effect, Engine};
use ttfx::utils::rng::Rng;

use crate::grid::parse_frame_into;

#[derive(Debug)]
pub(crate) enum AnimationError {
    BadArgument(String),
    State(String),
}

impl AnimationError {
    pub(crate) fn message(&self) -> &str {
        match self {
            Self::BadArgument(message) | Self::State(message) => message,
        }
    }

    pub(crate) fn is_bad_argument(&self) -> bool {
        matches!(self, Self::BadArgument(_))
    }
}

pub struct Animation {
    engine: Engine,
    effect: Box<dyn Effect>,
    cols: u16,
    rows: u16,
    name: String,
    pub glyphs: Vec<u32>,
    pub colors: Vec<u32>,
}

pub fn effect_names() -> Vec<String> {
    Cli::command()
        .get_subcommands()
        .map(|command| command.get_name().to_owned())
        .filter(|name| name != "help")
        .collect()
}

impl Animation {
    /// Creates an animation using extra ttfx CLI arguments and an optional deterministic seed.
    pub fn new(input: &str, args: &[String], cols: u16, rows: u16, seed: Option<u64>) -> Result<Self, String> {
        Self::construct(input, args, cols, rows, seed).map_err(|error| error.message().to_owned())
    }

    pub(crate) fn new_for_jni(
        input: &str,
        args: &[String],
        cols: u16,
        rows: u16,
        seed: Option<u64>,
    ) -> Result<Self, AnimationError> {
        Self::construct(input, args, cols, rows, seed)
    }

    fn construct(input: &str, args: &[String], cols: u16, rows: u16, seed: Option<u64>) -> Result<Self, AnimationError> {
        if cols == 0 || rows == 0 {
            return Err(AnimationError::BadArgument("canvas dimensions must be positive".to_owned()));
        }

        let mut command_line = vec![
            "ttfx".to_owned(),
            "--canvas-width".to_owned(),
            cols.to_string(),
            "--canvas-height".to_owned(),
            rows.to_string(),
            "--ignore-terminal-dimensions".to_owned(),
            "--anchor-canvas".to_owned(),
            "c".to_owned(),
            "--anchor-text".to_owned(),
            "c".to_owned(),
        ];
        command_line.extend_from_slice(args);
        let cli = Cli::try_parse_from(command_line).map_err(|error| AnimationError::BadArgument(error.to_string()))?;

        let config = cli.terminal_config();
        let mut rng = seed.map_or_else(Rng::from_entropy, Rng::seeded);
        let names = effect_names();
        let (command, name) = if cli.random_effect {
            let mut candidates = names;
            if !cli.include_effects.is_empty() {
                candidates.retain(|name| cli.include_effects.contains(name));
            }
            candidates.retain(|name| !cli.exclude_effects.contains(name));
            if candidates.is_empty() {
                return Err(AnimationError::BadArgument("No effects available after filtering.".to_owned()));
            }
            let name = candidates[rng.choice_index(candidates.len())].clone();
            let effect_cli = Cli::try_parse_from(["ttfx", name.as_str()])
                .map_err(|error| AnimationError::BadArgument(error.to_string()))?;
            let command = effect_cli.effect.ok_or_else(|| {
                AnimationError::State(format!("failed to build effect '{name}'"))
            })?;
            (command, name)
        } else {
            let name = args
                .iter()
                .find(|argument| names.contains(argument))
                .cloned()
                .ok_or_else(|| AnimationError::BadArgument("No effect specified.".to_owned()))?;
            let command = cli.effect.ok_or_else(|| AnimationError::BadArgument("No effect specified.".to_owned()))?;
            (command, name)
        };

        let clock = Clock::virtual_with_frame_rate(config.frame_rate);
        let mut effect = effects::build(&command)
            .ok_or_else(|| AnimationError::State(format!("{name} needs ttfx's original engine")))?;
        let mut engine = Engine::new(input, config, rng, clock).map_err(|error| AnimationError::BadArgument(error.to_string()))?;
        effect.build(&mut engine).map_err(|error| AnimationError::State(error.to_string()))?;

        let cell_count = usize::from(cols) * usize::from(rows);
        Ok(Self {
            engine,
            effect,
            cols,
            rows,
            name,
            glyphs: vec![0x20; cell_count],
            colors: vec![0; cell_count],
        })
    }

    /// Advances one frame; the grid remains at the last rendered frame when the effect ends.
    pub fn next_frame(&mut self) -> bool {
        if !self.effect.next_frame(&mut self.engine) {
            return false;
        }
        self.engine.clock.advance_frame();
        self.engine.render_here();

        let mut parts: Vec<IoSlice<'_>> = Vec::new();
        self.engine.renderer().frame_parts(b"", b"", &mut parts);
        let bytes: Vec<u8> = parts.iter().flat_map(|part| part.iter().copied()).collect();
        parse_frame_into(&bytes, self.cols, self.rows, &mut self.glyphs, &mut self.colors);
        true
    }

    pub fn effect(&self) -> &str {
        &self.name
    }
}

pub mod components;
pub mod native_app;
pub mod state;
pub mod theme;
pub mod views;

pub use native_app::launch_native_gui;

/// Launches the native Rust Obsidian Flight Deck 2.0 GUI window
pub fn launch_gui() {
    launch_native_gui();
}

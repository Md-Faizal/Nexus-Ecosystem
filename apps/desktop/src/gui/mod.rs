pub mod native_app;

pub use native_app::launch_native_gui;

/// Launches the native Rust SpaceX/xAI monochrome Flight Deck GUI window
pub fn launch_gui() {
    launch_native_gui();
}

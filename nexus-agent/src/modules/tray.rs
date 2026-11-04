#[cfg(target_os = "windows")]
use std::process::Command;
#[cfg(target_os = "windows")]
use std::ptr::{null, null_mut};
#[cfg(target_os = "windows")]
use tracing::info;

#[cfg(target_os = "windows")]
use windows_sys::Win32::Foundation::{HWND, LPARAM, LRESULT, POINT, WPARAM};
#[cfg(target_os = "windows")]
use windows_sys::Win32::System::Console::GetConsoleWindow;
#[cfg(target_os = "windows")]
use windows_sys::Win32::UI::Shell::{
    Shell_NotifyIconW, NIF_ICON, NIF_MESSAGE, NIF_TIP, NIM_ADD, NIM_DELETE, NOTIFYICONDATAW,
};
#[cfg(target_os = "windows")]
use windows_sys::Win32::UI::WindowsAndMessaging::{
    AppendMenuW, CreatePopupMenu, CreateWindowExW, DefWindowProcW, DestroyMenu,
    DispatchMessageW, GetCursorPos, GetMessageW, LoadIconW, PostQuitMessage, RegisterClassExW,
    SetForegroundWindow, ShowWindow, TrackPopupMenuEx, TranslateMessage, HICON, HMENU,
    IDI_APPLICATION, MF_DISABLED, MF_GRAYED, MF_SEPARATOR, MF_STRING, MSG, SW_HIDE,
    TPM_BOTTOMALIGN, TPM_RIGHTBUTTON, WM_COMMAND, WM_DESTROY, WM_LBUTTONDBLCLK, WM_RBUTTONUP,
    WM_USER, WNDCLASSEXW,
};

#[cfg(target_os = "windows")]
const WM_TRAYICON: u32 = WM_USER + 101;
#[cfg(target_os = "windows")]
const IDM_TITLE: usize = 1001;
#[cfg(target_os = "windows")]
const IDM_STATUS: usize = 1002;
#[cfg(target_os = "windows")]
const IDM_OPEN_ADMIN: usize = 1003;
#[cfg(target_os = "windows")]
const IDM_STATUS_CLI: usize = 1004;
#[cfg(target_os = "windows")]
const IDM_EXIT: usize = 1005;

#[cfg(target_os = "windows")]
static mut G_HWND: HWND = null_mut();

pub fn hide_console_window() {
    #[cfg(target_os = "windows")]
    unsafe {
        let hwnd = GetConsoleWindow();
        if hwnd != null_mut() {
            ShowWindow(hwnd, SW_HIDE);
        }
    }
}

pub fn start_system_tray(node_id: String) {
    #[cfg(target_os = "windows")]
    {
        std::thread::spawn(move || {
            unsafe {
                run_tray_loop(node_id);
            }
        });
    }
    #[cfg(not(target_os = "windows"))]
    {
        let _ = node_id;
    }
}

#[cfg(target_os = "windows")]
unsafe extern "system" fn window_proc(
    hwnd: HWND,
    msg: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    match msg {
        WM_TRAYICON => {
            let event = (lparam & 0xFFFF) as u32;
            if event == WM_RBUTTONUP {
                show_tray_menu(hwnd);
            } else if event == WM_LBUTTONDBLCLK {
                let _ = Command::new("cmd")
                    .args(["/c", "start", "https://faizal.uk/admin.html"])
                    .spawn();
            }
            0
        }
        WM_COMMAND => {
            let cmd_id = (wparam & 0xFFFF) as usize;
            match cmd_id {
                IDM_OPEN_ADMIN => {
                    let _ = Command::new("cmd")
                        .args(["/c", "start", "https://faizal.uk/admin.html"])
                        .spawn();
                }
                IDM_STATUS_CLI => {
                    let _ = Command::new("cmd")
                        .args(["/c", "start", "powershell", "-NoExit", "-Command", "nexus-agent status"])
                        .spawn();
                }
                IDM_EXIT => {
                    cleanup_tray(hwnd);
                    std::process::exit(0);
                }
                _ => {}
            }
            0
        }
        WM_DESTROY => {
            cleanup_tray(hwnd);
            PostQuitMessage(0);
            0
        }
        _ => DefWindowProcW(hwnd, msg, wparam, lparam),
    }
}

#[cfg(target_os = "windows")]
unsafe fn show_tray_menu(hwnd: HWND) {
    let menu: HMENU = CreatePopupMenu();
    if menu == null_mut() {
        return;
    }

    let mut cursor = POINT { x: 0, y: 0 };
    GetCursorPos(&mut cursor);

    let title = to_wide_str("⚡ Nexus Fleet Agent");
    let status = to_wide_str("● Status: Connected (faizal.uk)");
    let admin_label = to_wide_str("🌐 Open Personal Site Admin");
    let status_label = to_wide_str("📊 View Node Status (CLI)");
    let exit_label = to_wide_str("❌ Exit Agent");

    AppendMenuW(menu, MF_STRING | MF_DISABLED | MF_GRAYED, IDM_TITLE, title.as_ptr());
    AppendMenuW(menu, MF_STRING | MF_DISABLED | MF_GRAYED, IDM_STATUS, status.as_ptr());
    AppendMenuW(menu, MF_SEPARATOR, 0, null());
    AppendMenuW(menu, MF_STRING, IDM_OPEN_ADMIN, admin_label.as_ptr());
    AppendMenuW(menu, MF_STRING, IDM_STATUS_CLI, status_label.as_ptr());
    AppendMenuW(menu, MF_SEPARATOR, 0, null());
    AppendMenuW(menu, MF_STRING, IDM_EXIT, exit_label.as_ptr());

    SetForegroundWindow(hwnd);
    TrackPopupMenuEx(
        menu,
        TPM_RIGHTBUTTON | TPM_BOTTOMALIGN,
        cursor.x,
        cursor.y,
        hwnd,
        null(),
    );
    DestroyMenu(menu);
}

#[cfg(target_os = "windows")]
unsafe fn cleanup_tray(hwnd: HWND) {
    let mut nid = std::mem::zeroed::<NOTIFYICONDATAW>();
    nid.cbSize = std::mem::size_of::<NOTIFYICONDATAW>() as u32;
    nid.hWnd = hwnd;
    nid.uID = 1;
    Shell_NotifyIconW(NIM_DELETE, &nid);
}

#[cfg(target_os = "windows")]
unsafe fn run_tray_loop(node_id: String) {
    let class_name = to_wide_str("NexusAgentTrayClass");
    let window_title = to_wide_str("NexusAgentTrayWindow");

    let h_icon: HICON = LoadIconW(null_mut(), IDI_APPLICATION);

    let mut wc = std::mem::zeroed::<WNDCLASSEXW>();
    wc.cbSize = std::mem::size_of::<WNDCLASSEXW>() as u32;
    wc.lpfnWndProc = Some(window_proc);
    wc.lpszClassName = class_name.as_ptr();
    wc.hIcon = h_icon;

    if RegisterClassExW(&wc) == 0 {
        return;
    }

    let hwnd = CreateWindowExW(
        0,
        class_name.as_ptr(),
        window_title.as_ptr(),
        0,
        0,
        0,
        0,
        0,
        null_mut(),
        null_mut(),
        null_mut(),
        null(),
    );

    if hwnd == null_mut() {
        return;
    }

    G_HWND = hwnd;

    // Register Notification Icon in Taskbar Notification Area
    let mut nid = std::mem::zeroed::<NOTIFYICONDATAW>();
    nid.cbSize = std::mem::size_of::<NOTIFYICONDATAW>() as u32;
    nid.hWnd = hwnd;
    nid.uID = 1;
    nid.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
    nid.uCallbackMessage = WM_TRAYICON;
    nid.hIcon = h_icon;

    let tip_text = format!("Nexus Fleet Agent — Online ({})", node_id);
    let wide_tip = to_wide_str(&tip_text);
    let copy_len = wide_tip.len().min(nid.szTip.len() - 1);
    for i in 0..copy_len {
        nid.szTip[i] = wide_tip[i];
    }

    Shell_NotifyIconW(NIM_ADD, &nid);
    info!("Windows Taskbar Tray Icon initialized successfully for node {}", node_id);

    // Standard Win32 Message Loop
    let mut msg = std::mem::zeroed::<MSG>();
    while GetMessageW(&mut msg, null_mut(), 0, 0) > 0 {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
}

#[cfg(target_os = "windows")]
fn to_wide_str(s: &str) -> Vec<u16> {
    use std::os::windows::ffi::OsStrExt;
    std::ffi::OsStr::new(s)
        .encode_wide()
        .chain(std::iter::once(0))
        .collect()
}

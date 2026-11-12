use eframe::egui::{
    Color32, FontId, Pos2, Rect, Rounding, Sense, Stroke, Ui, Vec2,
};
use crate::gui::components::icons::{icon_button, IconKind};
use crate::gui::components::topology::links::{
    render_can_backbone_trunk, render_can_drop_cable, render_mesh_link,
};
use crate::gui::components::topology::node_card::{render_node_micro_hud, RenderNodeParams, TopologyRole};
use crate::gui::components::topology::packet_engine::{PacketKind, PacketParticle};
use crate::gui::state::{GuiState, TopologyViewMode};
use crate::gui::theme::*;

pub struct TopologyCanvas;

impl TopologyCanvas {
    pub fn render(
        ui: &mut Ui,
        state: &mut GuiState,
    ) {
        let avail_w = ui.available_width().max(320.0);
        let canvas_h = 440.0;

        // Reserve exact canvas size with click & drag sense
        let (rect, resp) = ui.allocate_exact_size(Vec2::new(avail_w, canvas_h), Sense::click_and_drag());
        let painter = ui.painter_at(rect);

        // Canvas Background & Border
        painter.rect(
            rect,
            Rounding::same(12.0),
            BG_VOID,
            Stroke::new(1.0_f32, BORDER_SUBTLE),
        );

        // Handle Pan via Dragging Canvas Background
        if resp.dragged() {
            state.topology_pan += resp.drag_delta();
        }

        // Handle Zoom via Mouse Wheel over Canvas
        if resp.hovered() {
            let scroll_delta = ui.input(|i| i.raw_scroll_delta.y);
            if scroll_delta.abs() > 0.0 {
                let factor = if scroll_delta > 0.0 { 1.08 } else { 0.92 };
                state.topology_zoom = (state.topology_zoom * factor).clamp(0.5, 2.2);
            }
        }

        // Subtle CAD Dots Grid transformed by Pan & Zoom
        let grid_step = 28.0 * state.topology_zoom;
        let offset_x = (state.topology_pan.x % grid_step + grid_step) % grid_step;
        let offset_y = (state.topology_pan.y % grid_step + grid_step) % grid_step;

        let mut gx = rect.left() + offset_x;
        while gx < rect.right() {
            let mut gy = rect.top() + offset_y;
            while gy < rect.bottom() {
                painter.circle_filled(
                    Pos2::new(gx, gy),
                    0.8,
                    Color32::from_rgba_unmultiplied(255, 255, 255, 12),
                );
                gy += grid_step;
            }
            gx += grid_step;
        }

        // Gather and classify nodes
        let raw_nodes = collect_topology_nodes(state);

        // Calculate node layout positions according to active view mode
        let center = rect.center() + state.topology_pan;
        let positioned_nodes = layout_nodes(&raw_nodes, center, state.topology_zoom, state.topology_view_mode);

        let time = if state.topology_frozen {
            0.0
        } else {
            ui.input(|i| i.time) as f32
        };

        // Render Links & Trunk
        let mut particles: Vec<PacketParticle> = Vec::new();
        match state.topology_view_mode {
            TopologyViewMode::CanBusHybrid => {
                let trunk_y = center.y;
                let trunk_left = center.x - (avail_w * 0.42 * state.topology_zoom).max(220.0);
                let trunk_right = center.x + (avail_w * 0.42 * state.topology_zoom).max(220.0);

                render_can_backbone_trunk(
                    &painter,
                    trunk_left,
                    trunk_right,
                    trunk_y,
                    "NEXUS CAN-BUS BACKBONE (500 kbps)",
                );

                // Render drop cables from trunk to each node
                for node in &positioned_nodes {
                    let drop_x = node.pos.x;
                    render_can_drop_cable(
                        &painter,
                        drop_x,
                        trunk_y,
                        node.pos,
                        node.role.accent_color(),
                        state.topology_show_link_metrics,
                        node.tx_bytes_sec,
                        node.rx_bytes_sec,
                        node.is_online,
                    );

                    // Generate real-time packet particles ONLY if node is ONLINE!
                    if state.topology_show_packets
                        && node.is_online
                        && (node.tx_bytes_sec > 0 || node.rx_bytes_sec > 0 || node.role == TopologyRole::Hub)
                    {
                        let trunk_pt = Pos2::new(drop_x, trunk_y);
                        let kind = match node.role {
                            TopologyRole::Hub => PacketKind::QuorumHeartbeat,
                            TopologyRole::Failover | TopologyRole::Quorum => PacketKind::QuorumHeartbeat,
                            TopologyRole::AiCompute => PacketKind::AiInferenceStream,
                            _ => PacketKind::CanTelemetry,
                        };

                        // Outbound stream: Node -> Trunk
                        particles.push(PacketParticle::new(
                            node.pos,
                            trunk_pt,
                            (node.id.len() as f32 * 0.23) % 1.0,
                            0.75,
                            kind,
                        ));

                        // Trunk to Hub flow if not hub itself
                        if node.role != TopologyRole::Hub {
                            particles.push(PacketParticle::new(
                                trunk_pt,
                                Pos2::new(center.x, trunk_y),
                                (node.id.len() as f32 * 0.37) % 1.0,
                                0.9,
                                kind,
                            ));
                        }
                    }
                }
            }
            TopologyViewMode::TieredHierarchy => {
                // Tiered links: Cloud Core -> Compute -> Edge
                let hub_node = positioned_nodes.iter().find(|n| n.role == TopologyRole::Hub);
                let hub_pos = hub_node.map(|n| n.pos).unwrap_or(center);
                let hub_online = hub_node.map(|n| n.is_online).unwrap_or(false);

                for node in &positioned_nodes {
                    if node.role != TopologyRole::Hub {
                        let link_online = node.is_online && hub_online;
                        render_mesh_link(
                            &painter,
                            hub_pos,
                            node.pos,
                            node.role.accent_color(),
                            1.2_f32,
                            state.topology_show_link_metrics,
                            if link_online { node.latency_ms } else { None },
                            node.tx_bytes_sec,
                            node.rx_bytes_sec,
                            link_online,
                        );

                        if state.topology_show_packets
                            && link_online
                            && (node.tx_bytes_sec > 0 || node.rx_bytes_sec > 0)
                        {
                            let kind = if node.role == TopologyRole::AiCompute {
                                PacketKind::AiInferenceStream
                            } else {
                                PacketKind::CanTelemetry
                            };
                            particles.push(PacketParticle::new(
                                node.pos,
                                hub_pos,
                                (node.id.len() as f32 * 0.31) % 1.0,
                                0.65,
                                kind,
                            ));
                        }
                    }
                }
            }
            TopologyViewMode::FullMesh => {
                // Star + Peer Quorum Triangle links
                let hub_node = positioned_nodes.iter().find(|n| n.role == TopologyRole::Hub);
                let hub_pos = hub_node.map(|n| n.pos).unwrap_or(center);
                let hub_online = hub_node.map(|n| n.is_online).unwrap_or(false);

                // Quorum triangle inter-connections
                let quorum_nodes: Vec<_> = positioned_nodes
                    .iter()
                    .filter(|n| matches!(n.role, TopologyRole::Hub | TopologyRole::Failover | TopologyRole::Quorum))
                    .collect();

                for i in 0..quorum_nodes.len() {
                    for j in (i + 1)..quorum_nodes.len() {
                        let link_online = quorum_nodes[i].is_online && quorum_nodes[j].is_online;
                        render_mesh_link(
                            &painter,
                            quorum_nodes[i].pos,
                            quorum_nodes[j].pos,
                            ACCENT_VIOLET,
                            1.4_f32,
                            state.topology_show_link_metrics,
                            if link_online { Some(12) } else { None },
                            if link_online { 1200 } else { 0 },
                            if link_online { 1200 } else { 0 },
                            link_online,
                        );
                        if state.topology_show_packets && link_online {
                            particles.push(PacketParticle::new(
                                quorum_nodes[i].pos,
                                quorum_nodes[j].pos,
                                (i as f32 * 0.4) % 1.0,
                                1.1,
                                PacketKind::QuorumHeartbeat,
                            ));
                        }
                    }
                }

                // Edge links to hub
                for node in &positioned_nodes {
                    if !matches!(node.role, TopologyRole::Hub | TopologyRole::Failover | TopologyRole::Quorum) {
                        let link_online = node.is_online && hub_online;
                        render_mesh_link(
                            &painter,
                            hub_pos,
                            node.pos,
                            node.role.accent_color(),
                            1.0_f32,
                            state.topology_show_link_metrics,
                            if link_online { node.latency_ms } else { None },
                            node.tx_bytes_sec,
                            node.rx_bytes_sec,
                            link_online,
                        );
                        if state.topology_show_packets
                            && link_online
                            && (node.tx_bytes_sec > 0 || node.rx_bytes_sec > 0)
                        {
                            particles.push(PacketParticle::new(
                                node.pos,
                                hub_pos,
                                (node.id.len() as f32 * 0.27) % 1.0,
                                0.7,
                                PacketKind::CanTelemetry,
                            ));
                        }
                    }
                }
            }
        }

        // Draw animated packet particles
        if state.topology_show_packets && !state.topology_frozen {
            for particle in &particles {
                particle.draw(&painter, time);
            }
        }

        // Render Node Cards
        let search_query = state.topology_filter.trim().to_lowercase();
        for node in &positioned_nodes {
            let is_matched = search_query.is_empty()
                || node.hostname.to_lowercase().contains(&search_query)
                || node.id.to_lowercase().contains(&search_query);

            if !is_matched {
                continue;
            }

            let is_sel = state.selected_node_id.as_deref() == Some(&node.id);
            let params = RenderNodeParams {
                id: &node.id,
                hostname: &node.hostname,
                role: node.role,
                os: &node.os,
                arch: &node.arch,
                is_online: node.is_online,
                is_this_pc: node.is_this_pc,
                cpu_pct: node.cpu_pct,
                ram_pct: node.ram_pct,
                vram_pct: node.vram_pct,
                models_count: node.models.len(),
                rx_bytes_sec: node.rx_bytes_sec,
                tx_bytes_sec: node.tx_bytes_sec,
                pos: node.pos,
                is_selected: is_sel,
            };

            render_node_micro_hud(ui, &painter, &params, &mut state.selected_node_id);
        }

        // Floating On-Canvas Overlay Toolbar (Top-Right)
        let toolbar_rect = Rect::from_min_size(
            Pos2::new(rect.left() + 12.0, rect.top() + 10.0),
            Vec2::new(rect.width() - 24.0, 30.0),
        );

        let mut toolbar_ui = ui.new_child(
            eframe::egui::UiBuilder::new()
                .max_rect(toolbar_rect)
                .layout(eframe::egui::Layout::left_to_right(eframe::egui::Align::Center)),
        );

        toolbar_ui.horizontal(|ui| {
            // View Mode Pill Toggle
            for mode in TopologyViewMode::ALL {
                let is_active = state.topology_view_mode == mode;
                let col = if is_active { ACCENT_CYAN } else { TEXT_3 };
                let bg = if is_active { BG_TILE } else { Color32::TRANSPARENT };
                if ui
                    .add(
                        eframe::egui::Button::new(
                            eframe::egui::RichText::new(mode.label())
                                .font(FontId::monospace(9.0))
                                .color(col),
                        )
                        .fill(bg)
                        .stroke(Stroke::new(1.0_f32, if is_active { BORDER_STRONG } else { Color32::TRANSPARENT })),
                    )
                    .clicked()
                {
                    state.topology_view_mode = mode;
                }
            }

            ui.add_space(8.0);
            ui.separator();
            ui.add_space(8.0);

            // Flow & Metrics Toggles
            let pkt_label = if state.topology_show_packets { "⚡ Packets" } else { "○ Packets" };
            if ui
                .add(
                    eframe::egui::Button::new(
                        eframe::egui::RichText::new(pkt_label)
                            .font(FontId::monospace(8.5))
                            .color(if state.topology_show_packets { ACCENT_AMBER } else { TEXT_3 }),
                    )
                    .fill(if state.topology_show_packets { BG_TILE } else { Color32::TRANSPARENT }),
                )
                .clicked()
            {
                state.topology_show_packets = !state.topology_show_packets;
            }

            let mtr_label = if state.topology_show_link_metrics { "📊 Metrics" } else { "○ Metrics" };
            if ui
                .add(
                    eframe::egui::Button::new(
                        eframe::egui::RichText::new(mtr_label)
                            .font(FontId::monospace(8.5))
                            .color(if state.topology_show_link_metrics { ACCENT_EMERALD } else { TEXT_3 }),
                    )
                    .fill(if state.topology_show_link_metrics { BG_TILE } else { Color32::TRANSPARENT }),
                )
                .clicked()
            {
                state.topology_show_link_metrics = !state.topology_show_link_metrics;
            }

            ui.with_layout(eframe::egui::Layout::right_to_left(eframe::egui::Align::Center), |ui| {
                if icon_button(ui, IconKind::Refresh, 22.0, TEXT_2, "Reset Viewport (100%)").clicked() {
                    state.topology_zoom = 1.0;
                    state.topology_pan = Vec2::ZERO;
                }

                if ui.button(eframe::egui::RichText::new("+").font(FontId::monospace(10.0))).clicked() {
                    state.topology_zoom = (state.topology_zoom * 1.15).min(2.2);
                }

                ui.label(
                    eframe::egui::RichText::new(format!("{:.0}%", state.topology_zoom * 100.0))
                        .font(FontId::monospace(8.5))
                        .color(TEXT_2),
                );

                if ui.button(eframe::egui::RichText::new("-").font(FontId::monospace(10.0))).clicked() {
                    state.topology_zoom = (state.topology_zoom * 0.85).max(0.5);
                }
            });
        });
    }
}

pub struct TopologyNodeData {
    pub id: String,
    pub hostname: String,
    pub role: TopologyRole,
    pub os: String,
    pub arch: String,
    pub is_online: bool,
    pub is_this_pc: bool,
    pub cpu_pct: f32,
    pub ram_pct: f32,
    pub vram_pct: Option<f32>,
    pub models: Vec<String>,
    pub rx_bytes_sec: u64,
    pub tx_bytes_sec: u64,
    pub latency_ms: Option<u64>,
    pub pos: Pos2,
}

pub(crate) fn collect_topology_nodes(state: &GuiState) -> Vec<TopologyNodeData> {
    let mut list: Vec<TopologyNodeData> = Vec::new();

    let local_id = &state.config.node_id;
    let local_host = &state.config.hostname;
    let local_cpu = state.telemetry.as_ref().map(|t| t.cpu.usage_pct).unwrap_or(0.0);
    let local_ram = state.telemetry.as_ref().map(|t| t.memory.used_pct).unwrap_or(0.0);
    let local_vram = state.gpu.as_ref().map(|g| g.utilization_pct);
    let local_rx = state.telemetry.as_ref().map(|t| t.network.rx_bytes_sec).unwrap_or(0);
    let local_tx = state.telemetry.as_ref().map(|t| t.network.tx_bytes_sec).unwrap_or(0);
    let local_models = state.ai_telemetry.models.clone();

    let mut local_added = false;
    let mut seen_ids = std::collections::HashSet::new();

    // Core Cloud Nodes to synthesize if not directly in remote_nodes
    let core_cloud_nodes = [
        ("my-mac", "my-mac", TopologyRole::Hub, "linux", "x86_64"),
        ("container-of-souls", "container-of-souls", TopologyRole::Failover, "linux", "x86_64"),
        ("prometheus", "prometheus", TopologyRole::Quorum, "linux", "x86_64"),
    ];

    for (node_id, host, role, os, arch) in core_cloud_nodes {
        let matching_remote = state.remote_nodes.iter().find(|n| {
            n.node_id.eq_ignore_ascii_case(node_id) || n.hostname.eq_ignore_ascii_case(host)
        });

        let is_online = if let Some(r) = matching_remote {
            r.status == "online"
        } else if role == TopologyRole::Hub {
            state.hub_latency_ms.is_some()
        } else {
            false
        };

        let (cpu, ram, rx, tx) = if is_online {
            let c = matching_remote
                .and_then(|n| n.telemetry.as_ref())
                .and_then(|t| t.get("cpu")?.get("usage_pct")?.as_f64())
                .map(|v| v as f32)
                .unwrap_or(14.0);
            let m = matching_remote
                .and_then(|n| n.telemetry.as_ref())
                .and_then(|t| t.get("memory")?.get("used_pct")?.as_f64())
                .map(|v| v as f32)
                .unwrap_or(38.0);
            let r_rx = if role == TopologyRole::Hub {
                state.total_fleet_tx_bytes_sec().max(800)
            } else {
                matching_remote
                    .and_then(|n| n.telemetry.as_ref())
                    .and_then(|t| t.get("network")?.get("rx_bytes_sec")?.as_u64())
                    .unwrap_or(0)
            };
            let r_tx = if role == TopologyRole::Hub {
                state.total_fleet_rx_bytes_sec().max(800)
            } else {
                matching_remote
                    .and_then(|n| n.telemetry.as_ref())
                    .and_then(|t| t.get("network")?.get("tx_bytes_sec")?.as_u64())
                    .unwrap_or(0)
            };
            (c, m, r_rx, r_tx)
        } else {
            (0.0, 0.0, 0, 0)
        };

        seen_ids.insert(node_id.to_string());
        list.push(TopologyNodeData {
            id: node_id.to_string(),
            hostname: host.to_string(),
            role,
            os: os.to_string(),
            arch: arch.to_string(),
            is_online,
            is_this_pc: false,
            cpu_pct: cpu,
            ram_pct: ram,
            vram_pct: None,
            models: vec![],
            rx_bytes_sec: rx,
            tx_bytes_sec: tx,
            latency_ms: if is_online { state.hub_latency_ms } else { None },
            pos: Pos2::ZERO,
        });
    }

    // Remote nodes reported by Hub
    for r in &state.remote_nodes {
        if seen_ids.contains(&r.node_id) {
            continue;
        }

        let is_this = (!local_id.is_empty() && r.node_id == *local_id)
            || (!local_host.is_empty() && r.hostname.eq_ignore_ascii_case(local_host));

        if is_this {
            local_added = true;
        }

        let is_online = r.status == "online";
        let role = classify_node_role(&r.hostname, &r.models);

        let (cpu, ram, rx, tx) = if is_online {
            let c = r
                .telemetry
                .as_ref()
                .and_then(|t| t.get("cpu")?.get("usage_pct")?.as_f64())
                .map(|v| v as f32)
                .unwrap_or(0.0);
            let m = r
                .telemetry
                .as_ref()
                .and_then(|t| t.get("memory")?.get("used_pct")?.as_f64())
                .map(|v| v as f32)
                .unwrap_or(0.0);
            let r_rx = r
                .telemetry
                .as_ref()
                .and_then(|t| t.get("network")?.get("rx_bytes_sec")?.as_u64())
                .unwrap_or(0);
            let r_tx = r
                .telemetry
                .as_ref()
                .and_then(|t| t.get("network")?.get("tx_bytes_sec")?.as_u64())
                .unwrap_or(0);
            (c, m, r_rx, r_tx)
        } else {
            // Strictly zero out CPU, RAM, and throughput for offline devices
            (0.0, 0.0, 0, 0)
        };

        seen_ids.insert(r.node_id.clone());
        list.push(TopologyNodeData {
            id: r.node_id.clone(),
            hostname: r.hostname.clone(),
            role,
            os: r.os.clone(),
            arch: r.arch.clone(),
            is_online,
            is_this_pc: is_this,
            cpu_pct: cpu,
            ram_pct: ram,
            vram_pct: None,
            models: if is_online { r.models.clone() } else { vec![] },
            rx_bytes_sec: rx,
            tx_bytes_sec: tx,
            latency_ms: None,
            pos: Pos2::ZERO,
        });
    }

    // If local PC not reported in remote nodes yet, insert it as primary workstation/compute
    if !local_added {
        let role = classify_node_role(local_host, &local_models);
        list.push(TopologyNodeData {
            id: local_id.clone(),
            hostname: local_host.clone(),
            role,
            os: state.config.os.clone(),
            arch: state.config.arch.clone(),
            is_online: true,
            is_this_pc: true,
            cpu_pct: local_cpu,
            ram_pct: local_ram,
            vram_pct: local_vram,
            models: local_models,
            rx_bytes_sec: local_rx,
            tx_bytes_sec: local_tx,
            latency_ms: state.hub_latency_ms,
            pos: Pos2::ZERO,
        });
    }

    list
}

pub(crate) fn classify_node_role(hostname: &str, models: &[String]) -> TopologyRole {
    let lower = hostname.to_lowercase();
    if lower.contains("my-mac") || lower.contains("hub") {
        TopologyRole::Hub
    } else if lower.contains("container-of-souls") || lower.contains("cos") {
        TopologyRole::Failover
    } else if lower.contains("potato-pro") || !models.is_empty() || lower.contains("gpu") {
        TopologyRole::AiCompute
    } else if lower.contains("prometheus") || lower == "pro" || lower.starts_with("pro.") {
        TopologyRole::Quorum
    } else if lower.contains("carplay") || lower.contains("tv") || lower.contains("phone") {
        TopologyRole::Ambient
    } else {
        TopologyRole::Workstation
    }
}

fn layout_nodes(
    nodes: &[TopologyNodeData],
    center: Pos2,
    zoom: f32,
    mode: TopologyViewMode,
) -> Vec<TopologyNodeData> {
    let mut positioned: Vec<TopologyNodeData> = Vec::with_capacity(nodes.len());

    match mode {
        TopologyViewMode::CanBusHybrid => {
            // Split into Cloud Core (above trunk) and Compute/Edge (below trunk)
            let (top_nodes, bottom_nodes): (Vec<_>, Vec<_>) = nodes
                .iter()
                .partition(|n| matches!(n.role, TopologyRole::Hub | TopologyRole::Failover | TopologyRole::Quorum));

            let top_spacing = 160.0 * zoom;
            let top_start_x = center.x - ((top_nodes.len().saturating_sub(1)) as f32 * 0.5 * top_spacing);
            for (idx, n) in top_nodes.iter().enumerate() {
                let mut node = (*n).clone();
                let x = top_start_x + idx as f32 * top_spacing;
                let y = center.y - 120.0 * zoom;
                node.pos = Pos2::new(x, y);
                positioned.push(node);
            }

            let bot_spacing = 160.0 * zoom;
            let bot_start_x = center.x - ((bottom_nodes.len().saturating_sub(1)) as f32 * 0.5 * bot_spacing);
            for (idx, n) in bottom_nodes.iter().enumerate() {
                let mut node = (*n).clone();
                let x = bot_start_x + idx as f32 * bot_spacing;
                let y = center.y + 120.0 * zoom;
                node.pos = Pos2::new(x, y);
                positioned.push(node);
            }
        }
        TopologyViewMode::TieredHierarchy => {
            // Level 1: Cloud Core Quorum (Y: center.y - 140)
            // Level 2: AI Compute Grid (Y: center.y)
            // Level 3: Edge Workstations & Ambient (Y: center.y + 140)
            let mut tier1 = Vec::new();
            let mut tier2 = Vec::new();
            let mut tier3 = Vec::new();

            for n in nodes {
                match n.role {
                    TopologyRole::Hub | TopologyRole::Failover | TopologyRole::Quorum => tier1.push(n),
                    TopologyRole::AiCompute => tier2.push(n),
                    _ => tier3.push(n),
                }
            }

            let tiers = [(tier1, -140.0 * zoom), (tier2, 0.0), (tier3, 140.0 * zoom)];
            for (tier_list, y_offset) in tiers {
                let spacing = 165.0 * zoom;
                let start_x = center.x - ((tier_list.len().saturating_sub(1)) as f32 * 0.5 * spacing);
                for (idx, n) in tier_list.iter().enumerate() {
                    let mut node = (*n).clone();
                    node.pos = Pos2::new(start_x + idx as f32 * spacing, center.y + y_offset);
                    positioned.push(node);
                }
            }
        }
        TopologyViewMode::FullMesh => {
            // Hub in center, all other nodes arranged radially in circular orbit
            let non_hub: Vec<_> = nodes.iter().filter(|n| n.role != TopologyRole::Hub).collect();
            let total = non_hub.len().max(1);
            let radius = 150.0 * zoom;

            for n in nodes {
                let mut node = (*n).clone();
                if node.role == TopologyRole::Hub {
                    node.pos = center;
                }
                positioned.push(node);
            }

            for (idx, n) in non_hub.iter().enumerate() {
                let angle = (idx as f32 / total as f32) * 2.0 * std::f32::consts::PI - std::f32::consts::FRAC_PI_2;
                let x = center.x + angle.cos() * radius;
                let y = center.y + angle.sin() * radius;
                if let Some(target) = positioned.iter_mut().find(|p| p.id == n.id) {
                    target.pos = Pos2::new(x, y);
                }
            }
        }
    }

    positioned
}

impl Clone for TopologyNodeData {
    fn clone(&self) -> Self {
        Self {
            id: self.id.clone(),
            hostname: self.hostname.clone(),
            role: self.role,
            os: self.os.clone(),
            arch: self.arch.clone(),
            is_online: self.is_online,
            is_this_pc: self.is_this_pc,
            cpu_pct: self.cpu_pct,
            ram_pct: self.ram_pct,
            vram_pct: self.vram_pct,
            models: self.models.clone(),
            rx_bytes_sec: self.rx_bytes_sec,
            tx_bytes_sec: self.tx_bytes_sec,
            latency_ms: self.latency_ms,
            pos: self.pos,
        }
    }
}

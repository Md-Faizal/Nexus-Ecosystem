use eframe::egui::{Color32, Painter, Pos2};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PacketKind {
    /// Sensor and hardware metrics stream (Edge -> Hub)
    CanTelemetry,
    /// Fast rhythmic consensus & failover ping (Cloud Core Quorum)
    QuorumHeartbeat,
    /// High-throughput AI tokens & prompt streams (AI Compute <-> Hub)
    AiInferenceStream,
    /// Remote control and orchestrator dispatches (Hub -> Node)
    ControlDispatch,
}

impl PacketKind {
    pub fn color(&self) -> Color32 {
        match self {
            PacketKind::CanTelemetry => Color32::from_rgb(56, 189, 248),    // ACCENT_CYAN
            PacketKind::QuorumHeartbeat => Color32::from_rgb(167, 139, 250), // ACCENT_VIOLET
            PacketKind::AiInferenceStream => Color32::from_rgb(245, 158, 11),// ACCENT_AMBER
            PacketKind::ControlDispatch => Color32::from_rgb(239, 68, 68),  // ACCENT_CRIMSON
        }
    }

    pub fn size(&self) -> f32 {
        match self {
            PacketKind::CanTelemetry => 3.0,
            PacketKind::QuorumHeartbeat => 2.5,
            PacketKind::AiInferenceStream => 3.8,
            PacketKind::ControlDispatch => 4.2,
        }
    }
}

pub struct PacketParticle {
    pub from: Pos2,
    pub to: Pos2,
    pub phase: f32,
    pub speed: f32,
    pub kind: PacketKind,
}

impl PacketParticle {
    pub fn new(from: Pos2, to: Pos2, phase: f32, speed: f32, kind: PacketKind) -> Self {
        Self {
            from,
            to,
            phase,
            speed,
            kind,
        }
    }

    pub fn draw(&self, painter: &Painter, time: f32) {
        let t = ((time * self.speed + self.phase) % 1.0 + 1.0) % 1.0;
        let base_col = self.kind.color();
        let base_size = self.kind.size();

        // Calculate current position
        let head = Pos2::new(
            self.from.x + (self.to.x - self.from.x) * t,
            self.from.y + (self.to.y - self.from.y) * t,
        );

        // Motion blur / comet tail (3 trailing points)
        let tail_steps = [0.035, 0.07, 0.11];
        let alphas = [110, 65, 25];
        for (i, &offset) in tail_steps.iter().enumerate() {
            let tail_t = t - offset;
            if tail_t > 0.0 {
                let tail_pos = Pos2::new(
                    self.from.x + (self.to.x - self.from.x) * tail_t,
                    self.from.y + (self.to.y - self.from.y) * tail_t,
                );
                let tail_col = Color32::from_rgba_unmultiplied(
                    base_col.r(),
                    base_col.g(),
                    base_col.b(),
                    alphas[i],
                );
                painter.circle_filled(tail_pos, (base_size * (1.0 - (i as f32 * 0.25))).max(1.0), tail_col);
            }
        }

        // Glowing outer halo
        painter.circle_filled(
            head,
            base_size * 2.2,
            Color32::from_rgba_unmultiplied(base_col.r(), base_col.g(), base_col.b(), 45),
        );

        // Solid particle core
        painter.circle_filled(head, base_size, base_col);

        // Intense center spec
        painter.circle_filled(head, base_size * 0.45, Color32::from_rgb(255, 255, 255));
    }
}

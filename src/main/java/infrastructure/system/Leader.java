package infrastructure.system;

import java.net.InetAddress;

public record Leader(InetAddress leaderIp, short leaderPort) {
}

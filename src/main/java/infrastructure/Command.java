package infrastructure;

public enum Command {
    START(0), START_ACK(1), HEALTH(2), HEALTH_ACK(3), ELECTION(4), LEADER_INFO(5);

    public final byte command;

    Command(int command) {
        this.command = ((byte) command);
    }
}

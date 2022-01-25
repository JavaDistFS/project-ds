package configuration;

import infrastructure.Command;
import infrastructure.client.RemoteClient;
import infrastructure.client.TcpClient;
import infrastructure.client.UdpClient;
import infrastructure.converter.ElectionPayloadConverter;
import infrastructure.converter.LeaderInfoPayloadConverter;
import infrastructure.converter.StartAckPayloadConverter;
import infrastructure.converter.StartPayloadConverter;
import infrastructure.handler.message.ElectionMessageHandler;
import infrastructure.handler.message.LeaderInfoMessageHandler;
import infrastructure.handler.message.tcp.FileUploadMessageHandler;
import infrastructure.handler.message.tcp.TcpMessageHandler;
import infrastructure.handler.message.udp.*;
import infrastructure.handler.request.RequestHandler;
import infrastructure.handler.request.TcpRequestHandler;
import infrastructure.handler.request.UdpRequestHandler;
import infrastructure.system.SystemContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import static infrastructure.system.IdService.nodeId;

public class Configuration {
    public static final int DEFAULT_LISTEN_PORT = 4711;

    private final static Logger LOG = LogManager.getLogger(Configuration.class);

    public RequestHandler<DatagramPacket> getDefaultClientRequestHandler() {
        return new UdpRequestHandler(udpMessageHandlers(getDefaultClient()));
    }

    private Map<Command, UdpMessageHandler> udpMessageHandlers(RemoteClient<DatagramPacket> client) {
        HashMap<Command, UdpMessageHandler> messageHandlers = new HashMap<>();
        messageHandlers.put(Command.START, new StartMessageHandler(client, new StartPayloadConverter()));
        messageHandlers.put(Command.START_ACK, new StartAckMessageHandler(client, new StartAckPayloadConverter()));
        messageHandlers.put(Command.HEALTH, new HealthMessageHandler(client, null));
        messageHandlers.put(Command.HEALTH_ACK, new HealthAckMessageHandler());

        messageHandlers.put(Command.ELECTION, new ElectionMessageHandler(client, new ElectionPayloadConverter()));
        messageHandlers.put(Command.LEADER_INFO, new LeaderInfoMessageHandler(client, new LeaderInfoPayloadConverter()));

        return messageHandlers;
    }

    public RequestHandler<byte[]> getReliableClientRequestHandler() {
        return new TcpRequestHandler(tcpMessageHandlers(getReliableClient()));
    }

    private Map<Command, TcpMessageHandler> tcpMessageHandlers(RemoteClient<byte[]> client) {
        HashMap<Command, TcpMessageHandler> messageHandlers = new HashMap<>();
        messageHandlers.put(Command.FILE_UPLOAD, new FileUploadMessageHandler(client));
        return messageHandlers;
    }

    public RemoteClient<DatagramPacket> getDefaultClient() {
        return new UdpClient();
    }

    public RemoteClient<byte[]> getReliableClient() {
        return new TcpClient();
    }

    public SystemContext getContext() {
        try {
            return new SystemContext(nodeId(InetAddress.getLocalHost(), DEFAULT_LISTEN_PORT), DEFAULT_LISTEN_PORT);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}
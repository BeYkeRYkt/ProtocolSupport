package protocolsupport.protocol.core.initial;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.util.concurrent.Future;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.concurrent.TimeUnit;

import protocolsupport.api.ProtocolVersion;
import protocolsupport.protocol.core.ChannelHandlers;
import protocolsupport.protocol.core.IPipeLineBuilder;
import protocolsupport.protocol.storage.ProtocolStorage;
import protocolsupport.utils.Utils;

public class InitialPacketDecoder extends SimpleChannelInboundHandler<ByteBuf> {

	@SuppressWarnings("serial")
	private static final EnumMap<ProtocolVersion, IPipeLineBuilder> pipelineBuilders = new EnumMap<ProtocolVersion, IPipeLineBuilder>(ProtocolVersion.class) {{
		put(ProtocolVersion.MINECRAFT_1_8, new protocolsupport.protocol.transformer.v_1_8.PipeLineBuilder());
		put(ProtocolVersion.MINECRAFT_1_7_10, new protocolsupport.protocol.transformer.v_1_7.PipeLineBuilder());
		put(ProtocolVersion.MINECRAFT_1_7_5, new protocolsupport.protocol.transformer.v_1_7.PipeLineBuilder());
		put(ProtocolVersion.MINECRAFT_1_6_4, new protocolsupport.protocol.transformer.v_1_6.PipeLineBuilder());
		put(ProtocolVersion.MINECRAFT_1_6_2, new protocolsupport.protocol.transformer.v_1_6.PipeLineBuilder());
		put(ProtocolVersion.MINECRAFT_1_5_2, new protocolsupport.protocol.transformer.v_1_5.PipeLineBuilder());
		put(ProtocolVersion.UNKNOWN, new protocolsupport.protocol.transformer.v_1_8.PipeLineBuilder());
	}};


	protected final ByteBuf receivedData = Unpooled.buffer();

	protected SocketAddress address;
	protected volatile boolean protocolSet = false;

	protected Future<?> responseTask;

	protected void scheduleTask(ChannelHandlerContext ctx, Runnable task, long delay, TimeUnit tu) {
		cancelTask();
		responseTask = ctx.executor().schedule(task, delay, tu);
	}

	protected void cancelTask() {
		if (responseTask != null) {
			responseTask.cancel(true);
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		cancelTask();
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception  {
		super.handlerRemoved(ctx);
		cancelTask();
	}

	@SuppressWarnings("deprecation")
	@Override
	public void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
		if (!buf.isReadable()) {
			return;
		}
		receivedData.writeBytes(buf);
		final Channel channel = ctx.channel();
		ProtocolVersion handshakeversion = ProtocolVersion.NOT_SET;
		receivedData.readerIndex(0);
		int firstbyte = receivedData.readUnsignedByte();
		switch (firstbyte) {
			case 0xFE: { //old ping
				try {
					if (receivedData.readableBytes() == 0) { //really old protocol probably
						scheduleTask(ctx, new Ping11ResponseTask(channel), 1000, TimeUnit.MILLISECONDS);
					} else if (receivedData.readUnsignedByte() == 1) {
						if (receivedData.readableBytes() == 0) {
							//1.5.2 probably
							scheduleTask(ctx, new Ping152ResponseTask(this, channel), 500, TimeUnit.MILLISECONDS);
						} else if (
							(receivedData.readUnsignedByte() == 0xFA) &&
							"MC|PingHost".equals(new String(Utils.toArray(receivedData.readBytes(receivedData.readUnsignedShort() * 2)), StandardCharsets.UTF_16BE))
						) { //1.6.*
							receivedData.readUnsignedShort();
							handshakeversion = ProtocolVersion.fromId(receivedData.readUnsignedByte());
						}
					}
				} catch (IndexOutOfBoundsException ex) {
				}
				break;
			}
			case 0x02: { //1.6 or 1.5.2 handshake
				try {
					handshakeversion = ProtocolVersion.fromId(receivedData.readUnsignedByte());
				} catch (IndexOutOfBoundsException ex) {
				}
				break;
			}
			default: { //1.7 or 1.8 handshake
				receivedData.readerIndex(0);
				ByteBuf data = getVarIntPrefixedData(receivedData);
				if (data != null) {
					handshakeversion = readNPHandshake(data);
				}
				break;
			}
		}
		//if we detected the protocol than we save it and process data
		if (handshakeversion != ProtocolVersion.NOT_SET) {
			setProtocol(channel, receivedData, handshakeversion);
		}
	}

	protected void setProtocol(final Channel channel, final ByteBuf input, ProtocolVersion version) throws Exception {
		if (protocolSet) {
			return;
		}
		protocolSet = true;
		ProtocolStorage.setProtocolVersion(Utils.getNetworkManagerSocketAddress(channel), version);
		channel.pipeline().remove(ChannelHandlers.INITIAL_DECODER);
		pipelineBuilders.get(version).buildPipeLine(channel, version);
		input.readerIndex(0);
		channel.pipeline().firstContext().fireChannelRead(input);
	}

	@SuppressWarnings("deprecation")
	private static ProtocolVersion readNPHandshake(ByteBuf data) {
		if (readVarInt(data) == 0x00) {
			return ProtocolVersion.fromId(readVarInt(data));
		}
		return ProtocolVersion.UNKNOWN;
	}

	private static ByteBuf getVarIntPrefixedData(final ByteBuf byteBuf) {
		final byte[] array = new byte[3];
		for (int i = 0; i < array.length; ++i) {
			if (!byteBuf.isReadable()) {
				return null;
			}
			array[i] = byteBuf.readByte();
			if (array[i] >= 0) {
				final int length = readVarInt(Unpooled.wrappedBuffer(array));
				if (byteBuf.readableBytes() < length) {
					return null;
				}
				return byteBuf.readBytes(length);
			}
		}
		throw new CorruptedFrameException("Packet length is wider than 21 bit");
	}

	private static int readVarInt(ByteBuf data) {
		int value = 0;
		int length = 0;
		byte b0;
		do {
			b0 = data.readByte();
			value |= (b0 & 0x7F) << (length++ * 7);
			if (length > 5) {
				throw new RuntimeException("VarInt too big");
			}
		} while ((b0 & 0x80) == 0x80);
		return value;
	}

}

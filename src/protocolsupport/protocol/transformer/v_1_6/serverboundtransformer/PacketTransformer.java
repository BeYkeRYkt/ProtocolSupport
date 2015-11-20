package protocolsupport.protocol.transformer.v_1_6.serverboundtransformer;

import io.netty.channel.Channel;

import java.util.Collection;

import net.minecraft.server.v1_8_R3.Packet;

import protocolsupport.protocol.PacketDataSerializer;

public interface PacketTransformer {

	public Collection<Packet<?>> tranform(Channel channel, int packetId, PacketDataSerializer serializer) throws Exception;

}

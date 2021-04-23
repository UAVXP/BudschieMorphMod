package de.budschie.bmorph.network;

import java.util.function.Supplier;

import de.budschie.bmorph.capabilities.IMorphCapability;
import de.budschie.bmorph.capabilities.MorphCapabilityAttacher;
import de.budschie.bmorph.morph.functionality.AbilityLookupTableHandler;
import de.budschie.bmorph.network.MorphRequestMorphIndexChange.RequestMorphIndexChangePacket;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.NetworkEvent.Context;

public class MorphRequestMorphIndexChange implements ISimpleImplPacket<RequestMorphIndexChangePacket>
{
	@Override
	public void encode(RequestMorphIndexChangePacket packet, PacketBuffer buffer)
	{
		buffer.writeInt(packet.requestedIndex);
	}

	@Override
	public RequestMorphIndexChangePacket decode(PacketBuffer buffer)
	{
		return new RequestMorphIndexChangePacket(buffer.readInt());
	}

	@Override
	public void handle(RequestMorphIndexChangePacket packet, Supplier<Context> ctx)
	{
		ctx.get().enqueueWork(() ->
		{
			LazyOptional<IMorphCapability> cap = ctx.get().getSender().getCapability(MorphCapabilityAttacher.MORPH_CAP);
			
			if(cap.isPresent())
			{
				IMorphCapability resolved = cap.resolve().get();
				
				if(packet.getRequestedIndex() >= resolved.getMorphList().getMorphArrayList().size() || packet.getRequestedIndex() < 0)
				{
					System.out.println("Player " + ctx.get().getSender().getName().getString() + " with UUID " + ctx.get().getSender().getUniqueID() + " has tried to send invalid data!");
				}
				else
				{
					resolved.deapplyAbilities(ctx.get().getSender());
					resolved.setMorph(packet.requestedIndex);
					resolved.getCurrentMorph().ifPresent(morph -> resolved.setCurrentAbilities(AbilityLookupTableHandler.getAbilitiesFor(morph)));
					resolved.applyAbilities(ctx.get().getSender());
					resolved.syncMorphChange(ctx.get().getSender());
				}
			}
		});
	}
	
	public static class RequestMorphIndexChangePacket
	{
		int requestedIndex;
		
		public RequestMorphIndexChangePacket(int requestedIndex)
		{
			this.requestedIndex = requestedIndex;
		}
		
		public int getRequestedIndex()
		{
			return requestedIndex;
		}
	}
}

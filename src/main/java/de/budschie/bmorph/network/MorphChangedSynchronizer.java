package de.budschie.bmorph.network;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import de.budschie.bmorph.morph.MorphHandler;
import de.budschie.bmorph.morph.MorphItem;
import de.budschie.bmorph.morph.MorphUtil;
import de.budschie.bmorph.network.MorphChangedSynchronizer.MorphChangedPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fmllegacy.network.NetworkEvent.Context;

public class MorphChangedSynchronizer implements ISimpleImplPacket<MorphChangedPacket>
{
	@Override
	public void encode(MorphChangedPacket packet, FriendlyByteBuf buffer)
	{
		buffer.writeUUID(packet.getPlayerUUID());
		
		buffer.writeBoolean(packet.getMorphIndex().isPresent());
		buffer.writeBoolean(packet.getMorphItem().isPresent());
		
		packet.getMorphIndex().ifPresent(index -> buffer.writeInt(index));
		packet.getMorphItem().ifPresent(item -> buffer.writeNbt(item.serialize()));
		
		buffer.writeInt(packet.getAbilities().size());
		
		for(String str : packet.getAbilities())
			buffer.writeUtf(str);
	}

	@Override
	public MorphChangedPacket decode(FriendlyByteBuf buffer)
	{
		UUID playerUUID = buffer.readUUID();
		boolean hasIndex = buffer.readBoolean(), hasItem = buffer.readBoolean();
		
		Optional<Integer> morphIndex = Optional.empty();
		Optional<MorphItem> morphItem = Optional.empty();
		
		if(hasIndex)
			morphIndex = Optional.of(buffer.readInt());
		
		if(hasItem)
			morphItem = Optional.of(MorphHandler.deserializeMorphItem(buffer.readNbt()));
		
		int amountOfAbilities = buffer.readInt();
		
		ArrayList<String> abilities = new ArrayList<>(amountOfAbilities);
		
		for(int i = 0; i < amountOfAbilities; i++)
			abilities.add(buffer.readUtf());
		
		return new MorphChangedPacket(playerUUID, morphIndex, morphItem, abilities);
	}

	@Override
	public void handle(MorphChangedPacket packet, Supplier<Context> ctx)
	{
		ctx.get().enqueueWork(() ->
		{
			if(Minecraft.getInstance().level != null)
				MorphUtil.morphToClient(packet.getMorphItem(), packet.getMorphIndex(), packet.getAbilities(), Minecraft.getInstance().level.getPlayerByUUID(packet.getPlayerUUID()));
		});
	}
	
	public static class MorphChangedPacket
	{
		UUID playerUUID;
		Optional<Integer> morphIndex;
		Optional<MorphItem> morphItem;
		ArrayList<String> abilities; 
		
		public MorphChangedPacket(UUID playerUUID, Optional<Integer> morphIndex, Optional<MorphItem> morphItem, ArrayList<String> abilities)
		{
			this.playerUUID = playerUUID;
			this.morphIndex = morphIndex;
			this.morphItem = morphItem;
			this.abilities = abilities;
		}
		
		public Optional<Integer> getMorphIndex()
		{
			return morphIndex;
		}
		
		public Optional<MorphItem> getMorphItem()
		{
			return morphItem;
		}
		
		public ArrayList<String> getAbilities()
		{
			return abilities;
		}
		
		public UUID getPlayerUUID()
		{
			return playerUUID;
		}
	}
}

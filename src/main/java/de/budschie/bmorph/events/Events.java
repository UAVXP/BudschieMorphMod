package de.budschie.bmorph.events;

import java.util.Optional;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.budschie.bmorph.api_interact.ShrinkAPIInteractor;
import de.budschie.bmorph.capabilities.IMorphCapability;
import de.budschie.bmorph.capabilities.MorphCapabilityAttacher;
import de.budschie.bmorph.capabilities.blacklist.BlacklistData;
import de.budschie.bmorph.capabilities.blacklist.ConfigManager;
import de.budschie.bmorph.capabilities.guardian.GuardianBeamCapabilityAttacher;
import de.budschie.bmorph.capabilities.guardian.GuardianBeamCapabilityHandler;
import de.budschie.bmorph.capabilities.guardian.IGuardianBeamCapability;
import de.budschie.bmorph.capabilities.pufferfish.IPufferfishCapability;
import de.budschie.bmorph.capabilities.pufferfish.PufferfishCapabilityHandler;
import de.budschie.bmorph.entity.MorphEntity;
import de.budschie.bmorph.json_integration.AbilityConfigurationHandler;
import de.budschie.bmorph.json_integration.MorphAbilityManager;
import de.budschie.bmorph.json_integration.MorphNBTHandler;
import de.budschie.bmorph.json_integration.VisualMorphDataHandler;
import de.budschie.bmorph.main.BMorphMod;
import de.budschie.bmorph.main.ServerSetup;
import de.budschie.bmorph.morph.MorphItem;
import de.budschie.bmorph.morph.MorphManagerHandlers;
import de.budschie.bmorph.morph.MorphUtil;
import de.budschie.bmorph.network.DisposePlayerMorphData;
import de.budschie.bmorph.network.MainNetworkChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fmllegacy.network.PacketDistributor;

@EventBusSubscriber
public class Events
{
	private static final Logger LOGGER = LogManager.getLogger();
	public static int AGGRO_TICKS_TO_PASS = 200;
	
	// This field indicates whether we should resolve the ability names or not
	public static final MorphAbilityManager MORPH_ABILITY_MANAGER = new MorphAbilityManager();
	public static final MorphNBTHandler MORPH_NBT_HANDLER = new MorphNBTHandler();
	public static final AbilityConfigurationHandler ABILITY_CONFIG_HANDLER = new AbilityConfigurationHandler();
	public static final VisualMorphDataHandler VISUAL_MORPH_DATA_HANDLER = new VisualMorphDataHandler();
	
	@SubscribeEvent
	public static void onRegisterCapabilities(RegisterCapabilitiesEvent event)
	{
		event.register(IMorphCapability.class);
		event.register(IPufferfishCapability.class);
		event.register(IGuardianBeamCapability.class);
	}
	
	@SubscribeEvent
	public static void onPlayerJoined(PlayerLoggedInEvent event)
	{	
		if(!event.getEntity().level.isClientSide)
		{
			Player player = event.getPlayer();
			
			LazyOptional<IMorphCapability> cap = player.getCapability(MorphCapabilityAttacher.MORPH_CAP);
			
			if(cap.isPresent())
			{
				MinecraftForge.EVENT_BUS.post(new PlayerMorphEvent.Server.Pre(player, cap.resolve().get(), cap.resolve().get().getCurrentMorph().orElse(null)));
				
//				ServerSetup.server.getPlayerList().getPlayers().forEach(serverPlayer -> cap.resolve().get().syncWithClient(event.getPlayer(), serverPlayer));
//				ServerSetup.server.getPlayerList().getPlayers().forEach(serverPlayer -> cap.resolve().get().syncWithClient(serverPlayer, (ServerPlayerEntity) event.getPlayer()));
				cap.resolve().get().getCurrentMorph().ifPresent(morph -> cap.resolve().get().setCurrentAbilities(MORPH_ABILITY_MANAGER.getAbilitiesFor(morph)));
				cap.resolve().get().syncWithClients(player);
				
				cap.resolve().get().applyHealthOnPlayer(player);
				cap.resolve().get().applyAbilities(player);
				
				MinecraftForge.EVENT_BUS.post(new PlayerMorphEvent.Server.Post(player, cap.resolve().get(), cap.resolve().get().getCurrentMorph().orElse(null)));
				
				PufferfishCapabilityHandler.synchronizeWithClients(player);
				PufferfishCapabilityHandler.synchronizeWithClient(player, (ServerPlayer) player);
				
				GuardianBeamCapabilityHandler.synchronizeWithClients(player);
				GuardianBeamCapabilityHandler.synchronizeWithClient(player, (ServerPlayer) player);
			}
		}
	}
	
	@SubscribeEvent
	public static void onDatapackSyncing(OnDatapackSyncEvent event)
	{
		if(event.getPlayer() == null)
		{
			BMorphMod.DYNAMIC_ABILITY_REGISTRY.syncWithClients();
			BMorphMod.VISUAL_MORPH_DATA.syncWithClients();
			
			ServerSetup.server.getPlayerList().getPlayers().forEach(player ->
			{
				IMorphCapability cap = MorphUtil.getCapOrNull(player);
				
				if(cap != null)
				{
					MorphUtil.morphToServer(cap.getCurrentMorphItem(), cap.getCurrentMorphIndex(), player);
				}
			});
		}
		else
		{
			BMorphMod.DYNAMIC_ABILITY_REGISTRY.syncWithClient(event.getPlayer());
			BMorphMod.VISUAL_MORPH_DATA.syncWithClient(event.getPlayer());
		}
	}
	
	@SubscribeEvent
	public static void onPlayerIsBeingLoaded(PlayerEvent.StartTracking event)
	{
		if(event.getTarget() instanceof Player)
		{
			Player player = (Player) event.getTarget();
			MorphUtil.processCap(player, resolved -> resolved.syncWithClient(player, (ServerPlayer) event.getPlayer()));
			
			PufferfishCapabilityHandler.synchronizeWithClient(player, (ServerPlayer) event.getPlayer());
			GuardianBeamCapabilityHandler.synchronizeWithClient(player, (ServerPlayer) event.getPlayer());
		}
	}
	
	@SubscribeEvent
	public static void onRegisterReloadResourceLoaders(AddReloadListenerEvent event)
	{
		event.addListener(ABILITY_CONFIG_HANDLER);
		event.addListener(MORPH_ABILITY_MANAGER);
		event.addListener(MORPH_NBT_HANDLER);
		event.addListener(VISUAL_MORPH_DATA_HANDLER);
	}
	
	@SubscribeEvent
	public static void onPlayerStoppedBeingLoaded(PlayerEvent.StopTracking event)
	{
		event.getPlayer().getCapability(GuardianBeamCapabilityAttacher.GUARDIAN_BEAM_CAP).ifPresent(cap ->
		{
			if(cap.getAttackedEntity().isPresent() && cap.getAttackedEntity().get() == event.getTarget().getId())
			{
				GuardianBeamCapabilityHandler.unattackServer(event.getPlayer());
			}
		});
		
		if(event.getTarget() instanceof Player)
		{
			// Tell the client to demorph the given player that is now not tracked.
			MainNetworkChannel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) event.getPlayer()), new DisposePlayerMorphData.DisposePlayerMorphDataPacket(event.getTarget().getUUID()));
		}
	}
	
	@SubscribeEvent
	public static void onPlayerKilledLivingEntity(LivingDeathEvent event)
	{		
		if(!event.getEntity().level.isClientSide && ServerSetup.server.getGameRules().getBoolean(BMorphMod.DO_MORPH_DROPS))
		{
			if(event.getSource().getEntity() instanceof Player)
			{
				Player player = (Player) event.getSource().getEntity();
				
				if(!(player instanceof FakePlayer))
				{
					LazyOptional<IMorphCapability> playerMorph = player.getCapability(MorphCapabilityAttacher.MORPH_CAP);
					
					if(playerMorph.isPresent())
					{
						MorphItem morphItem = MorphManagerHandlers.createMorphFromDeadEntity(event.getEntity());
						
						if(morphItem != null)
						{
							IMorphCapability resolved = playerMorph.resolve().get();
							boolean shouldMorph = !ConfigManager.INSTANCE.get(BlacklistData.class).isInBlacklist(event.getEntity().getType().getRegistryName());
							
							if(!resolved.getMorphList().contains(morphItem) && shouldMorph)
							{
								MorphEntity morphEntity = new MorphEntity(event.getEntity().level, morphItem);
								morphEntity.setPos(event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ());
								event.getEntity().level.addFreshEntity(morphEntity);
							}
						}
					}
				}				
			}
		}
	}
	
	@SubscribeEvent
	public static void onPlayerChangedDimension(PlayerChangedDimensionEvent event)
	{
		if(!event.getEntity().level.isClientSide)
		{
			MorphUtil.processCap(event.getPlayer(), resolved ->
			{
				resolved.syncWithClients(event.getPlayer());
			});
		}
	}
	
	@SubscribeEvent
	public static void onClonePlayer(PlayerEvent.Clone event)
	{
		// TODO: This may cause a crash under certain circumstances, so I should maybe replace this code!
		// I've tested it and it doesnt cause a crash. That's good.
		if(!(event.isWasDeath() && !ServerSetup.server.getGameRules().getBoolean(BMorphMod.KEEP_MORPH_INVENTORY)))
		{
			LazyOptional<IMorphCapability> oldCap = event.getOriginal().getCapability(MorphCapabilityAttacher.MORPH_CAP);
			LazyOptional<IMorphCapability> newCap = event.getPlayer().getCapability(MorphCapabilityAttacher.MORPH_CAP);
			
			if(oldCap.isPresent() && newCap.isPresent())
			{
				IMorphCapability oldResolved = oldCap.resolve().get();
				IMorphCapability newResolved = newCap.resolve().get();
				
				newResolved.setMorphList(oldResolved.getMorphList());
				
				MinecraftForge.EVENT_BUS.post(new PlayerMorphEvent.Server.Pre(event.getPlayer(), newResolved, newResolved.getCurrentMorph().orElse(null)));
				
				oldResolved.getCurrentMorphIndex().ifPresent(morph -> newResolved.setMorph(morph));
				oldResolved.getCurrentMorphItem().ifPresent(morph -> newResolved.setMorph(morph));
				newResolved.setCurrentAbilities(oldResolved.getCurrentAbilities());
				
				MinecraftForge.EVENT_BUS.post(new PlayerMorphEvent.Server.Post(event.getPlayer(), newResolved, newResolved.getCurrentMorph().orElse(null)));
			}
		}
	}
	
	@SubscribeEvent
	public static void onPlayerRespawnedEvent(PlayerRespawnEvent event)
	{
		if(!event.getPlayer().level.isClientSide && ServerSetup.server.getGameRules().getBoolean(BMorphMod.KEEP_MORPH_INVENTORY))
		{
			LazyOptional<IMorphCapability> cap = event.getPlayer().getCapability(MorphCapabilityAttacher.MORPH_CAP);
			
			if(cap.isPresent())
			{
				IMorphCapability resolved = cap.resolve().get();
				
				resolved.syncWithClients(event.getPlayer());
				resolved.applyHealthOnPlayer(event.getPlayer());
				resolved.applyAbilities(event.getPlayer());
			}
		}
	}
	
	@SubscribeEvent
	public static void onPlayerDeathEvent(LivingDeathEvent event)
	{
		if(event.getEntityLiving() instanceof Player && !event.getEntity().level.isClientSide)
		{
			Player player = (Player) event.getEntityLiving();
			
			LazyOptional<IMorphCapability> cap = player.getCapability(MorphCapabilityAttacher.MORPH_CAP);
			
			if(cap.isPresent())
			{
				IMorphCapability resolved = cap.resolve().get();
				
				resolved.deapplyAbilities(player);
				
				if(!ServerSetup.server.getGameRules().getBoolean(BMorphMod.KEEP_MORPH_INVENTORY))
				{
					for(MorphItem item : resolved.getMorphList().getMorphArrayList())
					{
						MorphEntity morphEntity = new MorphEntity(player.level, item);
						morphEntity.setPos(player.getX(), player.getY(), player.getZ());
						player.level.addFreshEntity(morphEntity);
					}
				}
			}
		}
	}
	
	@SubscribeEvent
	public static void onPlayerTakingDamage(LivingDamageEvent event)
	{
		if(event.getEntityLiving() instanceof Player)
		{
			Player player = (Player)event.getEntityLiving();
			
			LazyOptional<IMorphCapability> cap = player.getCapability(MorphCapabilityAttacher.MORPH_CAP);
			
			if(cap.isPresent())
			{
				IMorphCapability resolved = cap.resolve().get();				
			}
		}
		// Check if living is a Mob and therefore "evil"
		else if(event.getSource().getEntity() instanceof Player && event.getEntityLiving() instanceof Enemy && !event.getEntity().level.isClientSide)
		{
			Player source = (Player) event.getSource().getEntity();
			
			LazyOptional<IMorphCapability> cap = source.getCapability(MorphCapabilityAttacher.MORPH_CAP);
			aggro(cap.resolve().get(), ServerSetup.server.getGameRules().getInt(BMorphMod.MORPH_AGGRO_DURATION));
		}
	}
	
	@SubscribeEvent
	public static void onChangedPose(PlayerTickEvent event)
	{
//		event.player.setPose(Pose.SLEEPING);
//		event.player.setForcedPose(Pose.SWIMMING);
		
		if(event.phase == Phase.END)
		{
			MorphUtil.processCap(event.player, cap ->
			{
				
				if(cap.getCurrentMorph().isPresent())
				{
					if((event.player.getBoundingBox().maxY - event.player.getBoundingBox().minY) < 1 && event.player.getPose() == Pose.SWIMMING && !event.player.isSwimming())
					{
						event.player.setPose(Pose.STANDING);
					}
				}
			});
		}
	}
	
	@SubscribeEvent
	public static void onMorphedClient(PlayerMorphEvent.Client.Post event)
	{
		event.getPlayer().refreshDimensions();
	}
	
	@SubscribeEvent
	public static void onMorphedServer(PlayerMorphEvent.Server.Post event)
	{
		event.getPlayer().refreshDimensions();
	}
	
	private static void aggro(IMorphCapability capability, int aggroDuration)
	{
		capability.setLastAggroTimestamp(ServerSetup.server.getTickCount());
		capability.setLastAggroDuration(aggroDuration);
	}
	
	@SubscribeEvent
	public static void onTargetBeingSet(LivingSetAttackTargetEvent event)
	{
		if(event.getEntityLiving() instanceof Mob && event.getTarget() instanceof Player && event.getTarget() != event.getEntityLiving().getLastHurtByMob())
		{
			Player player = (Player) event.getTarget();
			Mob aggressor = (Mob) event.getEntityLiving();
			
			LazyOptional<IMorphCapability> cap = player.getCapability(MorphCapabilityAttacher.MORPH_CAP);
			
			if(cap.isPresent())
			{
				IMorphCapability resolved = cap.resolve().get();
				
				if(resolved.getCurrentMorph().isPresent())
				{
					if(!resolved.shouldMobsAttack() && (ServerSetup.server.getTickCount() - resolved.getLastAggroTimestamp()) > resolved.getLastAggroDuration())
						aggressor.setTarget(null);
					else
					{
						aggro(resolved, ServerSetup.server.getGameRules().getInt(BMorphMod.MORPH_AGGRO_DURATION));
					}
				}
				// Do this so that we can't morph to player, wait the 10 sec, and move back.
				else
					aggro(resolved, ServerSetup.server.getGameRules().getInt(BMorphMod.MORPH_AGGRO_DURATION));
			}
		}
	}
	
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onCalculatingAABB(EntityEvent.Size event)
	{
		if(event.getEntity() instanceof Player)
		{
			Player player = (Player) event.getEntity();
			LazyOptional<IMorphCapability> cap = player.getCapability(MorphCapabilityAttacher.MORPH_CAP);
			
			if(cap.isPresent())
			{
				float divisor = ShrinkAPIInteractor.getInteractor().getShrinkingValue(player);
				
				IMorphCapability resolved = cap.resolve().get();
				resolved.getCurrentMorph().ifPresent(item ->
				{					
					try
					{
						Entity createdEntity = item.createEntity(event.getEntity().level);
						createdEntity.setPose(event.getPose());
						
						// We do this as we apply our own sneaking logic as I couldn't figure out how to get the multiplier for the eye height... F in the chat plz
						EntityDimensions newSize = createdEntity.getDimensions(Pose.STANDING);
						
						if(ShrinkAPIInteractor.getInteractor().isShrunk(player))
						{
							newSize = newSize.scale(1.6f / divisor, 1 / divisor);
						}
						
						if(event.getPose() == Pose.CROUCHING)
							newSize = newSize.scale(1, .85f);
						
						event.setNewSize(newSize, false);
						//event.setNewEyeHeight(createdEntity.getEyeHeightAccess(event.getPose(), newSize));
						event.setNewEyeHeight(newSize.height * 0.85f);
						//event.setNewEyeHeight(player.getEyeHeightAccess(event.getPose(), createdEntity.getSize(event.getPose())));
					}
					catch(NullPointerException ex)
					{
						LOGGER.catching(ex);
						
						if(!player.level.isClientSide)
							MorphUtil.morphToServer(Optional.empty(), Optional.empty(), player);
						else
						{
							resolved.demorph();
							player.sendMessage(new TextComponent(ChatFormatting.RED + "Couldn't morph to " + item.getEntityType().getRegistryName().toString() + ". This is a compatability issue. If possible, report this to the mod author on GitHub."), new UUID(0, 0));
						}
					}
				});
			}
		}
	}
}

package de.budschie.bmorph.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Optional;

import org.lwjgl.glfw.GLFW;

import de.budschie.bmorph.capabilities.IMorphCapability;
import de.budschie.bmorph.capabilities.MorphCapabilityAttacher;
import de.budschie.bmorph.main.ClientSetup;
import de.budschie.bmorph.morph.FavouriteNetworkingHelper;
import de.budschie.bmorph.network.DeleteOrDropMorph;
import de.budschie.bmorph.network.MainNetworkChannel;
import de.budschie.bmorph.network.MorphRequestAbilityUsage;
import de.budschie.bmorph.network.MorphRequestMorphIndexChange.RequestMorphIndexChangePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent.KeyInputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

// Here happens all the client stuff. The name is fake
@EventBusSubscriber(bus = Bus.FORGE, value = Dist.CLIENT)
public class MorphGuiHandler
{
	private static int currentIndex = 0;
	
	private static Optional<AbstractMorphGui> currentMorphGui = Optional.empty();
	private static boolean guiHidden = true;
	
	private static ArrayList<EyeHeightChangePair> scheduledChanges = new ArrayList<>();
	
	public static void traverseToIndexAndSetGui()
	{
		Iterator<AbstractMorphGui> iterator = MorphGuiRegistry.REGISTRY.get().getValues().iterator();
		
		for(int i = 0; i < currentIndex; i++)
		{
			iterator.next();
		}
		
		// This feels stupid...
		currentMorphGui = Optional.of(iterator.next());
	}
	
	/** This method hides the previous morph UI, traverses, and then shows the new morph UI **/
	private static void updateCurrentMorphUI()
	{
		currentMorphGui.ifPresent(ui -> ui.hideGui());
		
		traverseToIndexAndSetGui();
		
		currentMorphGui.get().showGui();
	}
	
	public static Optional<AbstractMorphGui> getCurrentMorphGui()
	{
		return currentMorphGui;
	}
	
	public static void updateMorphUi()
	{
		if(!guiHidden)
		{
			hideGui();
			showGui();
		}
	}
	
	public static void showGui()
	{
		guiHidden = false;
		currentMorphGui.ifPresent(gui -> gui.showGui());
	}
	
	public static void hideGui()
	{
		guiHidden = true;
		currentMorphGui.ifPresent(gui -> gui.hideGui());
	}
	
	public static boolean canGuiBeDisplayed()
	{
		return !guiHidden && currentMorphGui.isPresent();
	}
	
	@SubscribeEvent
	public static void onPressedKey(ClientTickEvent event)
	{		
		if(event.phase == Phase.END)
		{
			if(Minecraft.getInstance().level != null)
			{	
				if(!currentMorphGui.isPresent())
					traverseToIndexAndSetGui();
				
				if(ClientSetup.TOGGLE_MORPH_UI.consumeClick())
				{				
					if (guiHidden)
						showGui();
					else
						hideGui();
				}
				
				if(canGuiBeDisplayed())
				{
					if (ClientSetup.SCROLL_DOWN_MORPH_UI.consumeClick())
						currentMorphGui.get().scroll(1);
		
					if (ClientSetup.SCROLL_UP_MORPH_UI.consumeClick())
						currentMorphGui.get().scroll(-1);
					
					if (ClientSetup.SCROLL_LEFT_MORPH_UI.consumeClick())
						currentMorphGui.get().horizontalScroll(-1);
		
					if (ClientSetup.SCROLL_RIGHT_MORPH_UI.consumeClick())
						currentMorphGui.get().horizontalScroll(1);
	
					
					if(ClientSetup.NEXT_MORPH_UI.consumeClick())
					{
						currentIndex++;
						currentIndex %= MorphGuiRegistry.REGISTRY.get().getValues().size();
						traverseToIndexAndSetGui();
						updateCurrentMorphUI();
					}
					
					if(ClientSetup.PREVIOUS_MORPH_UI.consumeClick())
					{
						currentIndex--;
						currentIndex %= MorphGuiRegistry.REGISTRY.get().getValues().size();
						traverseToIndexAndSetGui();
						updateCurrentMorphUI();
					}
					
					if(ClientSetup.TOGGLE_MORPH_FAVOURITE.consumeClick())
					{
						LazyOptional<IMorphCapability> cap = Minecraft.getInstance().player.getCapability(MorphCapabilityAttacher.MORPH_CAP);
						
						if(cap.isPresent())
						{
							IMorphCapability resolved = cap.resolve().get();
							int favouriteMorphIndex = currentMorphGui.get().getMorphIndex();
							
							if(favouriteMorphIndex < 0)
								System.out.println("Yo wat");
							else
							{
								if(resolved.getFavouriteList().containsMorphItem(resolved.getMorphList().getMorphArrayList().get(favouriteMorphIndex)))
									FavouriteNetworkingHelper.removeFavouriteMorph(favouriteMorphIndex);
								else
									FavouriteNetworkingHelper.addFavouriteMorph(favouriteMorphIndex);
							}
						}
						
						currentMorphGui.ifPresent(morphGui -> morphGui.onFavouriteChanged());
					}
				}
				
				if(ClientSetup.USE_ABILITY_KEY.consumeClick())
					MainNetworkChannel.INSTANCE.sendToServer(new MorphRequestAbilityUsage.MorphRequestAbilityUsagePacket());			
			}
		}
	}
	
	@SubscribeEvent
	public static void onPressedKeyboardKeyRaw(KeyInputEvent event)
	{
		if(canGuiBeDisplayed() && currentMorphGui.isPresent())
		{
			boolean glfwPress = event.getAction() == GLFW.GLFW_PRESS;
			
			int morphIndex = currentMorphGui.get().getMorphIndex(); 
			
			if(ClientSetup.MORPH_UI.consumeClick() && glfwPress)
			{
				MainNetworkChannel.INSTANCE.sendToServer(new RequestMorphIndexChangePacket(currentMorphGui.get().getMorphIndex()));
				
				if(guiHidden)
					showGui();
				else
					hideGui();
			}
			else if(morphIndex >= 0)
			{
				if(ClientSetup.DROP_CURRENT_MORPH.consumeClick() && glfwPress)
				{
					dropOrDelete(true, morphIndex);
				}
				else if(ClientSetup.DELETE_CURRENT_MORPH.consumeClick() && glfwPress)
				{
					dropOrDelete(false, morphIndex);
				}
			}
		}
	}
	
	private static void dropOrDelete(boolean drop, int currentMorphIndex)
	{
		MainNetworkChannel.INSTANCE.sendToServer(new DeleteOrDropMorph.DeleteOrDropMorphPacket(currentMorphIndex, drop));
	}
	
	@SubscribeEvent
	public static void onRenderOverlayEvent(RenderGameOverlayEvent.Post event)
	{
		if(canGuiBeDisplayed() && event.getType() == ElementType.TEXT && currentMorphGui.isPresent())
		{
			currentMorphGui.get().render(event.getMatrixStack());
		}
	}
	
	public static void scheduleEyeHeightChange(float newEyeHeight, Player player)
	{
		EyeHeightChangePair pair = new EyeHeightChangePair();
		pair.newEyeHeight = newEyeHeight;
		pair.player = player;
		scheduledChanges.add(pair);
	}
	
	// Yeah im not going to create a non-default constructor here as i am to lazy...
	private static class EyeHeightChangePair
	{
		float newEyeHeight;
		Player player;
	}
}

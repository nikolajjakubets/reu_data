/*
 * Copyright (C) 2004-2013 L2J DataPack
 * 
 * This file is part of L2J DataPack.
 * 
 * L2J DataPack is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * L2J DataPack is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package instances.RankuFloor;

import java.util.Calendar;

import l2r.gameserver.instancemanager.InstanceManager;
import l2r.gameserver.model.L2Party;
import l2r.gameserver.model.L2World;
import l2r.gameserver.model.Location;
import l2r.gameserver.model.actor.L2Npc;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.entity.Instance;
import l2r.gameserver.model.instancezone.InstanceWorld;
import l2r.gameserver.model.quest.Quest;
import l2r.gameserver.network.SystemMessageId;
import l2r.gameserver.network.serverpackets.SystemMessage;
import l2r.gameserver.util.Util;

/**
 * Tower of Infinitum (10th Floor) instance zone.
 * @author GKR
 */
public class RankuFloor extends Quest
{
	private static final int INSTANCEID = 143; // this is the client number
	private static final int RESET_HOUR = 6;
	private static final int RESET_MIN = 30;
	
	// NPCs
	private static final int GK_9 = 32752;
	private static final int CUBE = 32374;
	private static final int RANKU = 25542;
	
	private static final int SEAL_BREAKER_10 = 15516;
	
	private static final Location ENTRY_POINT = new Location(-19008, 277024, -15000);
	private static final Location EXIT_POINT = new Location(-19008, 277122, -13376);
	
	public RankuFloor(int questId, String name, String descr)
	{
		super(questId, name, descr);
		
		addStartNpc(GK_9);
		addStartNpc(CUBE);
		addTalkId(GK_9);
		addTalkId(CUBE);
		addKillId(RANKU);
	}
	
	@Override
	public String onTalk(L2Npc npc, L2PcInstance player)
	{
		String htmltext = null;
		
		if (npc.getNpcId() == GK_9)
		{
			htmltext = checkConditions(player);
			
			if (htmltext == null)
			{
				enterInstance(player, "Ranku.xml");
			}
		}
		else if (npc.getNpcId() == CUBE)
		{
			final InstanceWorld world = InstanceManager.getInstance().getWorld(npc.getInstanceId());
			if ((world != null) && (world.getInstanceId() == INSTANCEID))
			{
				world.removeAllowed(player.getObjectId());
				teleportPlayer(player, EXIT_POINT, 0);
			}
		}
		return htmltext;
	}
	
	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isSummon)
	{
		int instanceId = npc.getInstanceId();
		if (instanceId > 0)
		{
			Instance inst = InstanceManager.getInstance().getInstance(instanceId);
			InstanceWorld world = InstanceManager.getInstance().getWorld(npc.getInstanceId());
			inst.setSpawnLoc(EXIT_POINT);
			
			// Terminate instance in 10 min
			if ((inst.getInstanceEndTime() - System.currentTimeMillis()) > 600000)
			{
				inst.setDuration(600000);
			}
			
			inst.setEmptyDestroyTime(0);
			
			if ((world != null) && (world.getInstanceId() == INSTANCEID))
			{
				setReenterTime(world);
			}
			
			addSpawn(CUBE, -19056, 278732, -15000, 0, false, 0, false, instanceId);
		}
		return super.onKill(npc, killer, isSummon);
	}
	
	private String checkConditions(L2PcInstance player)
	{
		if (player.getParty() == null)
		{
			return "gk-noparty.htm";
		}
		else if (player.getParty().getLeaderObjectId() != player.getObjectId())
		{
			return "gk-noleader.htm";
		}
		
		return null;
	}
	
	private boolean checkTeleport(L2PcInstance player)
	{
		final L2Party party = player.getParty();
		
		if (party == null)
		{
			return false;
		}
		
		if (player.getObjectId() != party.getLeaderObjectId())
		{
			player.sendPacket(SystemMessageId.ONLY_PARTY_LEADER_CAN_ENTER);
			return false;
		}
		
		for (L2PcInstance partyMember : party.getMembers())
		{
			if (partyMember.getLevel() < 78)
			{
				final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_LEVEL_REQUIREMENT_NOT_SUFFICIENT);
				sm.addPcName(partyMember);
				party.broadcastPacket(sm);
				return false;
			}
			
			if (!Util.checkIfInRange(500, player, partyMember, true))
			{
				final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_IS_IN_LOCATION_THAT_CANNOT_BE_ENTERED);
				sm.addPcName(partyMember);
				party.broadcastPacket(sm);
				return false;
			}
			
			if (InstanceManager.getInstance().getPlayerWorld(player) != null)
			{
				final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.ALREADY_ENTERED_ANOTHER_INSTANCE_CANT_ENTER);
				sm.addPcName(partyMember);
				party.broadcastPacket(sm);
				return false;
			}
			
			final Long reenterTime = InstanceManager.getInstance().getInstanceTime(partyMember.getObjectId(), INSTANCEID);
			if (System.currentTimeMillis() < reenterTime)
			{
				final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_MAY_NOT_REENTER_YET);
				sm.addPcName(partyMember);
				party.broadcastPacket(sm);
				return false;
			}
			
			if (partyMember.getInventory().getInventoryItemCount(SEAL_BREAKER_10, -1, false) < 1)
			{
				final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_QUEST_REQUIREMENT_NOT_SUFFICIENT);
				sm.addPcName(partyMember);
				party.broadcastPacket(sm);
				return false;
			}
		}
		
		return true;
	}
	
	private int enterInstance(L2PcInstance player, String template)
	{
		int instanceId = 0;
		// check for existing instances for this player
		InstanceWorld world = InstanceManager.getInstance().getPlayerWorld(player);
		// existing instance
		if (world != null)
		{
			if (world.getInstanceId() != INSTANCEID)
			{
				player.sendPacket(SystemMessageId.ALREADY_ENTERED_ANOTHER_INSTANCE_CANT_ENTER);
				return 0;
			}
			teleportPlayer(player, ENTRY_POINT, world.getInstanceId());
			return world.getInstanceId();
		}
		
		if (!checkTeleport(player))
		{
			return 0;
		}
		
		instanceId = InstanceManager.getInstance().createDynamicInstance(template);
		world = new InstanceWorld();
		world.setInstanceId(instanceId);
		world.setTemplateId(INSTANCEID);
		world.setStatus(0);
		InstanceManager.getInstance().addWorld(world);
		_log.info("Tower of Infinitum - Ranku floor started " + template + " Instance: " + instanceId + " created by player: " + player.getName());
		
		for (L2PcInstance partyMember : player.getParty().getMembers())
		{
			teleportPlayer(partyMember, ENTRY_POINT, instanceId);
			partyMember.destroyItemByItemId("Quest", SEAL_BREAKER_10, 1, null, true);
			world.addAllowed(partyMember.getObjectId());
		}
		
		return instanceId;
	}
	
	public void setReenterTime(InstanceWorld world)
	{
		if (world.getInstanceId() == INSTANCEID)
		{
			// Reenter time should be cleared every Wed and Sat at 6:30 AM, so we set next suitable
			Calendar reenter;
			Calendar now = Calendar.getInstance();
			Calendar reenterPointWed = (Calendar) now.clone();
			reenterPointWed.set(Calendar.AM_PM, Calendar.AM);
			reenterPointWed.set(Calendar.MINUTE, RESET_MIN);
			reenterPointWed.set(Calendar.HOUR_OF_DAY, RESET_HOUR);
			reenterPointWed.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY);
			Calendar reenterPointSat = (Calendar) reenterPointWed.clone();
			reenterPointSat.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
			
			if (now.after(reenterPointSat))
			{
				reenterPointWed.add(Calendar.WEEK_OF_MONTH, 1);
				reenter = (Calendar) reenterPointWed.clone();
			}
			else
			{
				reenter = (Calendar) reenterPointSat.clone();
			}
			
			final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.INSTANT_ZONE_S1_RESTRICTED);
			sm.addInstanceName(world.getTemplateId());
			// set instance reenter time for all allowed players
			for (int objectId : world.getAllowed())
			{
				L2PcInstance player = L2World.getInstance().getPlayer(objectId);
				if ((player != null) && player.isOnline())
				{
					InstanceManager.getInstance().setInstanceTime(objectId, world.getTemplateId(), reenter.getTimeInMillis());
					player.sendPacket(sm);
				}
			}
		}
	}
	
	public static void main(String[] args)
	{
		new RankuFloor(-1, RankuFloor.class.getSimpleName(), "instances");
	}
}

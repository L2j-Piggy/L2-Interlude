/*
 * Copyright (c) 2013 L2jMobius
 *
 * Search skills by name or ID using server data (like Alt+G but server-side).
 */
package handlers.admincommandhandlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.l2jmobius.commons.util.StringUtil;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.handler.IAdminCommandHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.util.HtmlUtil;

/**
 * Skill Search using server data - opens from //admin menu.
 */
public class AdminSkillSearchServer implements IAdminCommandHandler
{
	private static final int PAGE_LIMIT = 15;

	private static final String[] ADMIN_COMMANDS =
	{
		"admin_skill_search_server",
		"admin_skill_desc"
	};

	@Override
	public boolean onCommand(String command, Player activeChar)
	{
		if (command.startsWith("admin_skill_desc"))
		{
			final StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken();
			if (st.countTokens() >= 2)
			{
				try
				{
					final int skillId = Integer.parseInt(st.nextToken());
					final int level = Integer.parseInt(st.nextToken());
					showSkillDesc(activeChar, skillId, level);
				}
				catch (NumberFormatException e)
				{
					activeChar.sendMessage("Usage: //skill_desc <skillId> <level>");
				}
			}
			return true;
		}

		if (!command.startsWith("admin_skill_search_server"))
		{
			return false;
		}

		final StringTokenizer st = new StringTokenizer(command, " ");
		st.nextToken();
		if (!st.hasMoreTokens())
		{
			final NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setFile(activeChar, "data/html/admin/skill_search.htm");
			html.replace("%data%", "");
			html.replace("%pages%", "");
			activeChar.sendPacket(html);
			return true;
		}

		final String text = st.nextToken();
		int page = 1;
		if (st.hasMoreTokens())
		{
			try
			{
				page = Integer.parseInt(st.nextToken());
			}
			catch (NumberFormatException e)
			{
				page = 1;
			}
		}

		searchResults(activeChar, text.trim(), page);
		return true;
	}

	private void searchResults(Player player, String text, int page)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(player, "data/html/admin/skill_search.htm");

		final String lower = text.toLowerCase();
		final boolean isNumeric = text.matches("\\d+");
		final int searchId = isNumeric ? Integer.parseInt(text) : -1;

		// One entry per skill id (level 1 for display)
		final Map<Integer, Skill> byId = new HashMap<>();
		final Collection<Skill> all = SkillData.getInstance().getAllSkills();
		for (Skill s : all)
		{
			if (s == null)
			{
				continue;
			}
			boolean match = isNumeric && s.getId() == searchId;
			if (!match && s.getName() != null)
			{
				match = s.getName().toLowerCase().contains(lower);
			}
			if (match)
			{
				// Keep level 1 or the one we already have (any level for display)
				if (!byId.containsKey(s.getId()) || s.getLevel() == 1)
				{
					byId.put(s.getId(), s);
				}
			}
		}

		final List<Skill> list = new ArrayList<>(byId.values());
		list.sort((a, b) -> Integer.compare(a.getId(), b.getId()));

		if (list.isEmpty())
		{
			html.replace("%data%", "<tr><td colspan=\"4\">No skills found for \"" + text + "\".</td></tr>");
			html.replace("%pages%", "");
			player.sendPacket(html);
			return;
		}

		final int totalPages = Math.min(100, HtmlUtil.countPageNumber(list.size(), PAGE_LIMIT));
		final int from = (page - 1) * PAGE_LIMIT;
		final int to = Math.min(from + PAGE_LIMIT, list.size());
		final List<Skill> pageList = list.subList(from, to);

		final SkillData sd = SkillData.getInstance();
		final StringBuilder sb = new StringBuilder();
		for (Skill s : pageList)
		{
			final int maxLevel = sd.getMaxLevel(s.getId());
			final int level = maxLevel > 0 ? maxLevel : s.getLevel();
			final String name = escapeHtml(s.getName() != null ? s.getName() : "?");
			StringUtil.append(sb,
				"<tr><td width=\"40\">", String.valueOf(s.getId()),
				"</td><td width=\"35\">", String.valueOf(level),
				"</td><td width=\"140\"><a action=\"bypass -h admin_skill_desc ", String.valueOf(s.getId()), " ", String.valueOf(level),
				"\">", name, "</a></td>",
				"<td width=\"50\"><button value=\"Add\" action=\"bypass admin_add_skill ", String.valueOf(s.getId()), " ", String.valueOf(level),
				"\" width=40 height=15 back=\"sek.cbui94\" fore=\"sek.cbui92\"></td></tr>");
		}
		html.replace("%data%", sb.toString());

		sb.setLength(0);
		for (int i = 0; i < totalPages; i++)
		{
			final int p = i + 1;
			if (p == page)
			{
				sb.append(p).append("&nbsp;");
			}
			else
			{
				sb.append("<a action=\"bypass -h admin_skill_search_server ").append(text).append(" ").append(p).append("\">").append(p).append("</a>&nbsp;");
			}
		}
		html.replace("%pages%", sb.toString());
		player.sendPacket(html);
	}

	private void showSkillDesc(Player player, int skillId, int level)
	{
		final Skill skill = SkillData.getInstance().getSkill(skillId, level);
		if (skill == null)
		{
			player.sendMessage("Skill not found: " + skillId + " level " + level);
			return;
		}
		final String name = escapeHtml(skill.getName() != null ? skill.getName() : "?");
		final StringBuilder sb = new StringBuilder();
		sb.append("<html><body><center><table width=280 bgcolor=444444><tr><td><font color=LEVEL>Skill: ").append(name).append("</font></td></tr></table><br>");
		sb.append("<table width=280>");
		sb.append("<tr><td width=120>Id / Level:</td><td>").append(skill.getId()).append(" / ").append(skill.getLevel()).append("</td></tr>");
		sb.append("<tr><td>Reuse (sec):</td><td>").append(skill.getReuseDelay() / 1000.0).append("</td></tr>");
		sb.append("<tr><td>MP Consume:</td><td>").append(skill.getMpConsume()).append("</td></tr>");
		sb.append("<tr><td>Target:</td><td>").append(skill.getTargetType() != null ? skill.getTargetType().name() : "?").append("</td></tr>");
		sb.append("<tr><td>Power:</td><td>").append(skill.getPower()).append("</td></tr>");
		sb.append("<tr><td>Cast range:</td><td>").append(skill.getCastRange()).append("</td></tr>");
		sb.append("<tr><td>Effect range:</td><td>").append(skill.getEffectRange()).append("</td></tr>");
		sb.append("<tr><td>Hit time (ms):</td><td>").append(skill.getHitTime()).append("</td></tr>");
		sb.append("<tr><td>Active:</td><td>").append(skill.isActive()).append("</td></tr>");
		sb.append("<tr><td>Passive:</td><td>").append(skill.isPassive()).append("</td></tr>");
		sb.append("<tr><td>Magic:</td><td>").append(skill.isMagic()).append("</td></tr>");
		sb.append("<tr><td>Physical:</td><td>").append(skill.isPhysical()).append("</td></tr>");
		sb.append("</table><br><button value=\"Back\" action=\"bypass -h admin_skill_search_server\" width=80 height=21 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"></center></body></html>");
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}

	private static String escapeHtml(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	@Override
	public String[] getCommandList()
	{
		return ADMIN_COMMANDS;
	}
}

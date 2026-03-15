/*
 * Copyright (c) 2013 L2jMobius
 *
 * Search NPCs by name or ID using server data (like Alt+G but server-side).
 */
package handlers.admincommandhandlers;

import java.util.List;
import java.util.StringTokenizer;

import org.l2jmobius.commons.util.StringUtil;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.handler.IAdminCommandHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.util.HtmlUtil;

/**
 * NPC Search using server data - opens from //admin menu.
 */
public class AdminNpcSearchServer implements IAdminCommandHandler
{
	private static final int PAGE_LIMIT = 15;

	private static final String[] ADMIN_COMMANDS =
	{
		"admin_npc_search_server"
	};

	@Override
	public boolean onCommand(String command, Player activeChar)
	{
		if (!command.startsWith("admin_npc_search_server"))
		{
			return false;
		}

		final StringTokenizer st = new StringTokenizer(command, " ");
		st.nextToken();
		if (!st.hasMoreTokens())
		{
			final NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setFile(activeChar, "data/html/admin/npc_search.htm");
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
		html.setFile(player, "data/html/admin/npc_search.htm");

		final String lower = text.toLowerCase();
		final boolean isNumeric = text.matches("\\d+");
		final int searchId = isNumeric ? Integer.parseInt(text) : -1;

		final List<NpcTemplate> list = NpcData.getInstance().getTemplates(t -> {
			if (isNumeric && t.getId() == searchId)
			{
				return true;
			}
			return t.getName() != null && t.getName().toLowerCase().contains(lower);
		});

		if (list.isEmpty())
		{
			html.replace("%data%", "<tr><td colspan=\"5\">No NPCs found for \"" + text + "\".</td></tr>");
			html.replace("%pages%", "");
			player.sendPacket(html);
			return;
		}

		final int totalPages = Math.min(100, HtmlUtil.countPageNumber(list.size(), PAGE_LIMIT));
		final int from = (page - 1) * PAGE_LIMIT;
		final int to = Math.min(from + PAGE_LIMIT, list.size());
		final List<NpcTemplate> pageList = list.subList(from, to);

		final StringBuilder sb = new StringBuilder();
		for (NpcTemplate t : pageList)
		{
			final String name = t.getName() != null ? t.getName() : "?";
			StringUtil.append(sb,
				"<tr><td width=\"40\">", String.valueOf(t.getId()),
				"</td><td width=\"120\">", name,
				"</td><td width=\"35\">", String.valueOf(t.getLevel()),
				"</td><td width=\"50\">", t.getType() != null ? t.getType() : "?",
				"</td><td width=\"50\"><button value=\"Spawn\" action=\"bypass -h admin_spawn_monster ", String.valueOf(t.getId()),
				"\" width=50 height=15 back=\"sek.cbui94\" fore=\"sek.cbui92\"></td></tr>");
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
				sb.append("<a action=\"bypass -h admin_npc_search_server ").append(text).append(" ").append(p).append("\">").append(p).append("</a>&nbsp;");
			}
		}
		html.replace("%pages%", sb.toString());
		player.sendPacket(html);
	}

	@Override
	public String[] getCommandList()
	{
		return ADMIN_COMMANDS;
	}
}

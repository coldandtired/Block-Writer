package me.coldandtired.block_writer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class Main extends JavaPlugin 
{
	private Map<String, bw_Text> bw_data = new HashMap<String, bw_Text>();	
	private Map<Character, String> chars = new HashMap<Character, String>();	
	private Document doc = null;
	protected FileConfiguration config = null;
	
	@Override
	public void onDisable() 
	{
		bw_data = null;
		chars = null;
		doc = null;
		config = null;
	}

	@Override
	public void onEnable() 
	{
		if (!setup())
		{
			log("[Block Writer] No chars file found - stopping!");
			setEnabled(false);
		}
		else
		{
			PluginDescriptionFile pdf = this.getDescription();
			log("[Block Writer] Version " + pdf.getVersion() + " Enabled!");
		}
	}	
	
	private boolean setup()
	{
		try
		{
			String loc = this.getDataFolder().getPath() + File.separator + "chars.txt";
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			doc = factory.newDocumentBuilder().parse(new File(loc));
		}
		catch (Exception ne)
		{
			ne.printStackTrace();
			return false;
		}
		NodeList list =  doc.getElementsByTagName("char");
		for (int i = 0; i < list.getLength(); i++) 
		{
			Element el = (Element)list.item(i);	
			chars.put(el.getAttribute("value").charAt(0), el.getAttribute("blocks"));
		}
		if (config == null)	config = getConfig(); 
		else
		{
			File f = new File(getDataFolder() + File.separator + "config.yml");
			if (f.exists())
			try 
			{
				config.load(f);
			} 
			catch (Exception ne) 
			{
				ne.printStackTrace();
			}
		}
		return true;	
	}
	
	private void write_chars(String[] args, Player p)
	{	
		for (String s : args)
		for (String s2 : config.getString("banned_words", "").split(" ")) 
			if (s.equalsIgnoreCase(s2.trim()))
			{
				p.sendMessage(ChatColor.RED + "[Block Writer] Disallowed word!");
				log("[Block Writer] " + p.getName() + " tried to write a banned word");
				return;
			}
		
		bw_Text bwt = bw_data.get(p.getName());
		if (bwt == null) bwt = new bw_Text();
		
		Block block = p.getTargetBlock(null, config.getInt("reach", 25)).getRelative(BlockFace.UP);
		
		bwt.start_block = block;
		
		double yaw = ((p.getLocation().getYaw() + 22.5) % 360);
		if (yaw < 0) yaw = 360 + yaw;
		BlockFace bf = null;
		if (yaw > 65 && yaw <= 155) bf = BlockFace.EAST;
		else if (yaw > 155 && yaw <= 245) bf = BlockFace.SOUTH;
		else if (yaw > 245 && yaw <= 335) bf = BlockFace.WEST;
		else if (yaw > 335 || yaw <= 65) bf = BlockFace.NORTH;

		bwt.bf = bf;
		bw_data.put(p.getName(), bwt);

		backup_blocks(count_blocks(args), p.getName());
		
		int count = 0;
		boolean custom = false;
		
		if (bwt.materials != null) custom = true;

		MaterialData md;
		ArrayList<MaterialData> mds = null;
		
		if (custom) mds = bwt.materials;			
		else		
		{
			String s;
			if (config.get("default_material") instanceof String)
				s = config.getString("default_material");
			else s = Integer.toString(config.getInt("default_material", 5));
			mds = get_materials(s.split(" "), p);
		}
		md = mds.get(0);
		
		count = mds.size();
		
		Block block2 = block;
		for (String s : args)
		{
			for (char c : s.toCharArray())
			{
				String[] squares = chars.get(c).split(",");

				for (String s2 : squares)
				{
					Integer j = Integer.parseInt(s2.trim());
					block2 = block.getRelative(bf, j / 7);
					block2.getRelative(BlockFace.UP, j % 7).setTypeIdAndData(md.getItemTypeId(), md.getData(), false);
				}
				
				if (mds.size() > 1) 
				{
					count++;
					md = mds.get(count % mds.size());
				}
				block = block2.getRelative(bf, 2);
			}
			block = block2.getRelative(bf, 6);
		}		
	}
	
	
	void log(Object message)
	{
		String s;
		if (message instanceof Integer) s = Integer.toString((Integer)message); 
		else s = (String)message; 
		getServer().getLogger().info(s);
	}
	
	
	private ArrayList<MaterialData> get_materials(String[] args, Player p)
	{
		ArrayList<MaterialData> mds = new ArrayList<MaterialData>();
		for (String arg : args)
		if (arg.length() > 0)
		{
			String[] numbers = arg.split(":");
			try
			{
				Material mat = Material.getMaterial(Integer.parseInt(numbers[0]));			
		
				if (mat != null) 
				{
					for (String s : args)
						for (String s2 : config.getString("banned_materials", "6 8 9 10 11 26 27 28").split(" ")) if (s.equalsIgnoreCase(s2.trim()))
						{
							p.sendMessage(ChatColor.RED + "[Block Writer] That material isn't allowed!");
							return null;
						}
					MaterialData md = new MaterialData(mat);
					if (numbers.length == 2) md.setData(Byte.parseByte(numbers[1]));
					mds.add(md);
				}
				else
				{
					p.sendMessage(ChatColor.RED + "[Block Writer] Bad material ID");
					return null;
				}	
			}
			catch (Exception ne)
			{
				p.sendMessage(ChatColor.RED + "[Block Writer] Number IDs only!");
				return null;
			}
		}
		return mds;
	}
	
	
	private int count_blocks(String[] args)
	{
		int count = 0;
		for (String s : args)
		{
			for (char c : s.toCharArray())
			{
				String[] squares = chars.get(c).split(",");
				int temp = 0;
				for (String s2 : squares)
				{
					Integer i = Integer.parseInt(s2.trim());
					if (i > temp) temp = i;
				}
				count += (temp / 7) + 1;
			}
			count += (s.length() - 1);
		}
		count += ((args.length - 1) * 5);
		return count;
	}
	
	
	private void set_materials(String[] args, Player p)
	{
		bw_Text bwt = bw_data.get(p.getName());
		if (bwt == null) bwt = new bw_Text();
		ArrayList<MaterialData> mds = get_materials(args, p);		
		if (mds == null) return;
		{
			bwt.materials = mds;
			bw_data.put(p.getName(), bwt);
			p.sendMessage(ChatColor.GREEN + "[Block Writer] Materials changed!");
		}
	}
	
	
	private void restore_blocks(String p)
	{
		bw_Text bwt = bw_data.get(p);
		Block block = bwt.start_block;
		Block block2 = block;
		for (int i = 0; i < 7; i++)
		{
			for (int j = 0; j < bwt.backup[0].length; j++)
			{
				block2.setTypeIdAndData(bwt.backup[i][j].getItemTypeId(), bwt.backup[i][j].getData(), false);
				block2 = block2.getRelative(bwt.bf);
			}
			block = block.getRelative(BlockFace.UP);
			block2 = block;
		}
		bwt.backup = null;
	}
	
	
	private void backup_blocks(int columns, String p)
	{
		bw_Text bwt = bw_data.get(p);
		bwt.backup = new MaterialData[7][columns];
		Block block = bwt.start_block;
		Block block2 = block;
		
		for (int i = 0; i < 7; i++)
		{
			for (int j = 0; j < columns; j++)
			{
				bwt.backup[i][j] = new MaterialData(block2.getType(), block2.getData());
				block2 = block2.getRelative(bwt.bf);
			}
			block = block.getRelative(BlockFace.UP);
			block2 = block;
		}
	}
	
	
	private void remove_blocks(String arg, Player p)
	{
		try
		{
			int columns = Integer.parseInt(arg);
		
			double yaw = ((p.getLocation().getYaw() + 22.5) % 360);
			if (yaw < 0) yaw = 360 + yaw;
			BlockFace bf = null;
			if (yaw > 65 && yaw <= 155) bf = BlockFace.EAST;
			else if (yaw > 155 && yaw <= 245) bf = BlockFace.SOUTH;
			else if (yaw > 245 && yaw <= 335) bf = BlockFace.WEST;
			else if (yaw > 335 || yaw <= 65) bf = BlockFace.NORTH;
		
			bw_Text bwt = bw_data.get(p.getName());
			if (bwt == null) bwt = new bw_Text();
			bwt.bf = bf;			
		
			Block b = p.getTargetBlock(null, config.getInt("reach", 25)).getRelative(BlockFace.UP);
			Block b2 = b;
			bwt.start_block = b;
			bw_data.put(p.getName(), bwt);
		
			backup_blocks(columns, p.getName());
			for (int k = 0; k < 7; k++)
			{			
				b2 = b;
				for (int l = 0; l < columns; l++)
				{					
					b2.setType(Material.AIR);
					b2 = b2.getRelative(bf);
				}	
				b = b.getRelative(BlockFace.UP);
			}
		} 
		catch (NumberFormatException ex) 
		{
			p.sendMessage(ChatColor.RED + "[Block Writer] Must be a number!");
			return;
		}
	}
	
	
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
	{
		if (!(sender instanceof Player)) return false;
		Player p = (Player)sender;
		
		if (cmd.getName().equalsIgnoreCase("undo_blocks") && args.length == 0)
		{
			bw_Text bwt = bw_data.get(p.getName());
			if (bwt.backup != null) restore_blocks(p.getName());
			else p.sendMessage(ChatColor.RED + "[Block Writer] Nothing to undo!");
			return true;
		}
		
		if (cmd.getName().equalsIgnoreCase("set_material") && args.length > 0)
		{
			set_materials(args, p);
			return true;
		}

		if (cmd.getName().equalsIgnoreCase("write_blocks") && args.length > 0)
		{				
			write_chars(args, p);
			return true;
		}
		
		if (cmd.getName().equalsIgnoreCase("count_blocks") && args.length > 0)
		{
			p.sendMessage(ChatColor.GREEN + "[Block Writer] That would need " + count_blocks(args) + " blocks");
			return true;
		}
		
		if (cmd.getName().equalsIgnoreCase("reload_block_writer") && args.length == 0)
		{
			setup();
			p.sendMessage(ChatColor.GREEN + "[Block Writer] Config reloaded!");
			return true;
		}
		
		if (cmd.getName().equalsIgnoreCase("remove_blocks") && args.length == 1)
		{
			remove_blocks(args[0], p);
			return true;
		}
		
		return false;
	}

}

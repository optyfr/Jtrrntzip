package JTrrntzip;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import org.apache.commons.io.FilenameUtils;

public final class Program implements StatusCallback, LogCallback
{
	public static boolean NoRecursion = false;
	public static boolean ForceReZip = false;
	public static boolean CheckOnly = false;
	public static boolean VerboseLogging = false;
	private static boolean _guiLaunch;

	private static TorrentZip tz;

	public static void main(String[] args)
	{
		if(args.length == 0)
		{
			System.out.println("");
			System.out.println("trrntzip: missing path");
			System.out.println("Usage: trrntzip [OPTIONS] [PATH/ZIP FILES]");
			return;
		}

		for(int i = 0; i < args.length; i++)
		{
			String arg = args[i];
			if(arg.length() < 2)
				continue;
			if(arg.substring(0, 1) != "-")
				continue;

			switch(arg.substring(1, 2))
			{
				case "?":
					System.out.format("Jtrrentzip v%s\n", Program.class.getPackage().getSpecificationVersion());
					System.out.format("Copyright (C) 2018 opty");
					System.out.format("Homepage : http://www.romvault.com/trrntzip\n");
					System.out.format("Usage: trrntzip [OPTIONS] [PATH/ZIP FILE]\n");
					System.out.format("Options:\n");
					System.out.format("-? : show this help");
					System.out.format("-s : prevent sub-directory recursion");
					System.out.format("-f : force re-zip");
					System.out.format("-c : Check files only do not repair");
					System.out.format("-l : verbose logging");
					System.out.format("-v : show version");
					System.out.format("-g : pause when finished");
					return;
				case "s":
					NoRecursion = true;
					break;
				case "f":
					ForceReZip = true;
					break;
				case "c":
					CheckOnly = true;
					break;
				case "l":
					VerboseLogging = true;
					break;
				case "v":
					System.out.format("TorrentZip v%s", Program.class.getPackage().getSpecificationVersion());
					return;
				case "g":
					_guiLaunch = true;
					break;

			}
		}

		new Program();

		for(int i = 0; i < args.length; i++)
		{
			String arg = args[i];
			if(arg.length() < 2)
				continue;
			if(arg.substring(0, 1) == "-")
				continue;

			File argfile = new File(arg);
			// first check if arg is a directory
			if(argfile.isDirectory())
			{
				try
				{
					ProcessDir(argfile);
				}
				catch(IOException e)
				{
					e.printStackTrace();
				}
				continue;
			}

			// now check if arg is a directory/filename with possible wild cards.
			String dir = argfile.getParent();
			if(dir == null)
				dir = Paths.get(".").toAbsolutePath().normalize().toString();

			String filename = argfile.getName();

			try(DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get(dir), filename))
			{
				dirStream.forEach(path -> {
					String ext = FilenameUtils.getExtension(path.getFileName().toString());
					if(ext != null && (ext.equalsIgnoreCase("zip") || ext.equalsIgnoreCase("7z")))
					{
						try
						{
							tz.Process(path.toFile());
						}
						catch(IOException e)
						{
							e.printStackTrace();
						}
					}
				});
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}

		if(_guiLaunch)
		{
			System.out.format("Complete.");
			new Scanner(System.in).nextLine();
		}
	}

	public Program()
	{
		tz = new TorrentZip();
		tz.StatusCallBack = this;
		tz.StatusLogCallBack = this;
	}

	@Override
	public void StatusLogCallBack(int processId, String log)
	{
		System.out.format("%s\n", log);
	}

	@Override
	public void StatusCallBack(int processID, int percent)
	{
		System.out.format("%03d%%", percent);
	}

	private static void ProcessDir(File dir) throws IOException
	{
		System.out.println("Checking Dir : " + dir);

		for(File f : dir.listFiles())
		{
			if(f.isDirectory())
			{
				if(!NoRecursion)
					ProcessDir(f);
			}
			else
			{
				String ext = FilenameUtils.getExtension(f.getName());
				if(ext != null && (ext.equalsIgnoreCase("zip") || ext.equalsIgnoreCase("7z")))
				{
					tz.Process(f);
				}
			}
		}
	}

}

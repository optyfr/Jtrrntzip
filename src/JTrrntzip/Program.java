package JTrrntzip;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import org.apache.commons.io.FilenameUtils;

public final class Program extends AbstractTorrentZipOptions implements LogCallback
{
	public static void main(String[] args)
	{
		if(args.length == 0)
		{
			System.out.println("");
			System.out.println("trrntzip: missing path");
			System.out.println("Usage: trrntzip [OPTIONS] [PATH/ZIP FILES]");
			return;
		}

		new Program(args);

	}

	private TorrentZip tz;

	public Program(String[] args)
	{
		super(args);

		if(argfiles != null && argfiles.size() > 0)
		{
			tz = new TorrentZip(this, this);
			for(File argfile : argfiles)
			{
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
						if(ext != null && (ext.equalsIgnoreCase("zip")))
						{
							try
							{
								ProcessFile(path.toFile());
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
		}
		if(_guiLaunch)
		{
			System.out.format("Complete.");
			new Scanner(System.in).nextLine();
		}
	}

	private void ProcessDir(File dir) throws IOException
	{
		if(isVerboseLogging())
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
				if(ext != null && (ext.equalsIgnoreCase("zip")))
				{
					tz.Process(f);
				}
			}
		}
	}

	private void ProcessFile(File file) throws IOException
	{
		tz.Process(file);
	}

	@Override
	public final void StatusLogCallBack(String log)
	{
		System.out.format("%s\n", log);
	}

	@Override
	public final void StatusCallBack(int percent)
	{
		System.out.format("%03d%% ", percent);
	}

	@Override
	public final boolean isVerboseLogging()
	{
		return VerboseLogging;
	}

}

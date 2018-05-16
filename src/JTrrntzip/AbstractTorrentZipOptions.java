package JTrrntzip;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AbstractTorrentZipOptions implements TorrentZipOptions
{
	protected boolean NoRecursion = false;
	protected boolean ForceReZip = false;
	protected boolean CheckOnly = false;
	protected boolean VerboseLogging = false;
	protected boolean _guiLaunch = false;

	protected List<File> argfiles = null;

	public AbstractTorrentZipOptions(final String[] args)
	{
		final List<File> argfiles = new ArrayList<>();
		for(final String arg : args)
		{
			switch(arg)
			{
				case "-?":
					System.out.format("Jtrrentzip v%s\n", Program.class.getPackage().getSpecificationVersion());
					System.out.format("Copyright (C) 2018 opty\n");
					System.out.format("Based on trrntzipDN by GordonJ\n");
					System.out.format("Usage: trrntzip [OPTIONS] [PATH/ZIP FILE]\n");
					System.out.format("Options:\n");
					System.out.format("-? : show this help\n");
					System.out.format("-s : prevent sub-directory recursion\n");
					System.out.format("-f : force re-zip\n");
					System.out.format("-c : Check files only do not repair\n");
					System.out.format("-l : verbose logging\n");
					System.out.format("-v : show version\n");
					System.out.format("-g : pause when finished\n");
					return;
				case "-s":
					NoRecursion = true;
					break;
				case "-f":
					ForceReZip = true;
					break;
				case "-c":
					CheckOnly = true;
					break;
				case "-l":
					VerboseLogging = true;
					break;
				case "-v":
					System.out.format("TorrentZip v%s", Program.class.getPackage().getSpecificationVersion());
					return;
				case "-g":
					_guiLaunch = true;
					break;
				default:
					argfiles.add(new File(arg));
					break;
			}
		}
		this.argfiles = argfiles;
	}

	@Override
	public boolean isForceRezip()
	{
		return ForceReZip;
	}

	@Override
	public boolean isCheckOnly()
	{
		return CheckOnly;
	}

}

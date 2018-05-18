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
				case "-?": //$NON-NLS-1$
					System.out.format("Jtrrentzip v%s\n", Program.class.getPackage().getSpecificationVersion()); //$NON-NLS-1$
					System.out.format(Messages.getString("AbstractTorrentZipOptions.Copyright")); //$NON-NLS-1$
					System.out.format(Messages.getString("AbstractTorrentZipOptions.BasedOnTrrntzipDN")); //$NON-NLS-1$
					System.out.format(Messages.getString("AbstractTorrentZipOptions.Usage")); //$NON-NLS-1$
					System.out.format(Messages.getString("AbstractTorrentZipOptions.Options")); //$NON-NLS-1$
					System.out.format(Messages.getString("AbstractTorrentZipOptions.ShowThisHelp")); //$NON-NLS-1$
					System.out.format(Messages.getString("AbstractTorrentZipOptions.PreventSubDirRecursion")); //$NON-NLS-1$
					System.out.format(Messages.getString("AbstractTorrentZipOptions.ForceReZip")); //$NON-NLS-1$
					System.out.format(Messages.getString("AbstractTorrentZipOptions.CheckOnly")); //$NON-NLS-1$
					System.out.format(Messages.getString("AbstractTorrentZipOptions.VerboseLogging")); //$NON-NLS-1$
					System.out.format(Messages.getString("AbstractTorrentZipOptions.ShowVersion")); //$NON-NLS-1$
					System.out.format(Messages.getString("AbstractTorrentZipOptions.PauseWhenFinished")); //$NON-NLS-1$
					return;
				case "-s": //$NON-NLS-1$
					NoRecursion = true;
					break;
				case "-f": //$NON-NLS-1$
					ForceReZip = true;
					break;
				case "-c": //$NON-NLS-1$
					CheckOnly = true;
					break;
				case "-l": //$NON-NLS-1$
					VerboseLogging = true;
					break;
				case "-v": //$NON-NLS-1$
					System.out.format("TorrentZip v%s", Program.class.getPackage().getSpecificationVersion()); //$NON-NLS-1$
					return;
				case "-g": //$NON-NLS-1$
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

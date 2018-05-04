package JTrrntzip;

public class SimpleTorrentZipOptions implements TorrentZipOptions
{
	private boolean forceRezip = false;
	private boolean checkOnly = false;

	public SimpleTorrentZipOptions()
	{
	}

	public SimpleTorrentZipOptions(boolean forceRezip, boolean checkOnly)
	{
		this.forceRezip = forceRezip;
		this.checkOnly = checkOnly;
	}

	@Override
	public boolean isForceRezip()
	{
		return forceRezip;
	}

	@Override
	public boolean isCheckOnly()
	{
		return checkOnly;
	}

}

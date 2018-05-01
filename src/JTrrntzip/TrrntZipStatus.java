package JTrrntzip;

public enum TrrntZipStatus
{
	ValidTrrntzip(1),
	CorruptZip(2),
	NotTrrntzipped(4),
	BadDirectorySeparator(8),
	Unsorted(16),
	ExtraDirectoryEntries(32),
	RepeatFilesFound(64);

	private int status;

	private TrrntZipStatus(int status)
	{
		this.status = status;
	}
	
	public int getStatus()
	{
		return status;
	}
}

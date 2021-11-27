package jtrrntzip;

public enum ZipStatus
{
	None(0x0),
	TrrntZip(0x1),
	ExtraData(0x2);

	private int status;

	private ZipStatus(final int status)
	{
		this.status = status;
	}

	public int getStatus()
	{
		return status;
	}
}

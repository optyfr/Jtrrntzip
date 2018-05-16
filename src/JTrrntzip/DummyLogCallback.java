package JTrrntzip;

public class DummyLogCallback implements LogCallback
{
	public DummyLogCallback()
	{
	}

	@Override
	public void StatusCallBack(final int percent)
	{
	}

	@Override
	public boolean isVerboseLogging()
	{
		return false;
	}

	@Override
	public void StatusLogCallBack(final String log)
	{
	}

}

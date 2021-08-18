package JTrrntzip;

public class DummyLogCallback implements LogCallback
{
	public DummyLogCallback()
	{
	}

	@Override
	public void statusCallBack(final int percent)
	{
	}

	@Override
	public boolean isVerboseLogging()
	{
		return false;
	}

	@Override
	public void statusLogCallBack(final String log)
	{
	}

}

package JTrrntzip;

public class DummyLogCallback implements LogCallback
{
	public DummyLogCallback()
	{
	}

	@Override
	public void StatusCallBack(int percent)
	{
	}

	@Override
	public boolean isVerboseLogging()
	{
		return false;
	}

	@Override
	public void StatusLogCallBack(String log)
	{
	}

}

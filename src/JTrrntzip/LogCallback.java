package JTrrntzip;

public interface LogCallback extends StatusCallback
{
	boolean isVerboseLogging();
	void StatusLogCallBack(String log);
}

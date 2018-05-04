package JTrrntzip;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.commons.codec.binary.Hex;

public class ZippedFile
{
	public int Index;
	public String Name;
	public BigInteger Size;
	public int CRC;

	public final byte[] getCRC()
	{
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.putInt(CRC);
		return bb.array();
	}

	public final void setCRC(byte[] value)
	{
		CRC = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getInt();
	}
	
	public final String toString()
	{
		return Hex.encodeHexString(getCRC());
	}
}

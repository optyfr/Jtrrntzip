package JTrrntzip.SupportedFiles;

import java.math.BigInteger;

public abstract class UnsignedTypes
{
	public static long fromULong(BigInteger bi)
	{
		return bi.longValue();
	}

	public static int fromUInt(long l)
	{
		return (int) l;
	}

	public static short fromUShort(int i)
	{
		return (short) i;
	}

	public static BigInteger toULong(long value)
	{
		if(value >= 0L)
			return BigInteger.valueOf(value);
		else
		{
			int upper = (int) (value >>> 32);
			int lower = (int) value;
			// return (upper << 32) + lower
			return (BigInteger.valueOf(Integer.toUnsignedLong(upper))).shiftLeft(32).add(BigInteger.valueOf(Integer.toUnsignedLong(lower)));
		}
	}

	public static long toUInt(int value)
	{
		return Integer.toUnsignedLong(value);
	}

	public static int toUShort(short value)
	{
		return Short.toUnsignedInt(value);
	}
}

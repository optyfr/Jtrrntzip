package JTrrntzip.SupportedFiles.SevenZip;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import JTrrntzip.ZipOpenType;
import JTrrntzip.ZipReturn;
import JTrrntzip.SupportedFiles.ICompress;

public class SevenZ implements ICompress
{

	public SevenZ()
	{
		// TODO Auto-generated constructor stub
	}

	@Override
	public int LocalFilesCount()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String Filename(int i)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long LocalHeader(int i)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long UncompressedSize(int i)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public byte[] CRC32(int i)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ZipReturn FileStatus(int i)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ZipOpenType ZipOpen()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ZipReturn ZipFileOpen(File newFilename, long timestamp, boolean readHeaders) throws IOException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void ZipFileClose() throws IOException
	{
		// TODO Auto-generated method stub

	}

	public ZipReturn ZipFileOpenWriteStream(boolean raw, boolean trrntzip, String filename, long uncompressedSize, short compressionMethod, AtomicReference<OutputStream> stream) throws IOException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ZipReturn ZipFileCloseReadStream() throws IOException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void DeepScan()
	{
		// TODO Auto-generated method stub

	}

	@Override
	public EnumSet<JTrrntzip.ZipStatus> ZipStatus()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String ZipFilename()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long TimeStamp()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void ZipFileAddDirectory() throws IOException
	{
		// TODO Auto-generated method stub

	}

	@Override
	public ZipReturn ZipFileCreate(File newFilename) throws IOException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ZipReturn ZipFileCloseWriteStream(byte[] crc32) throws IOException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ZipReturn ZipFileRollBack() throws IOException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void ZipFileCloseFailed() throws IOException
	{
		// TODO Auto-generated method stub

	}

	public ZipReturn ZipFileOpenReadStream(int index, AtomicReference<InputStream> stream, AtomicLong streamSize) throws IOException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void close() throws IOException
	{
		// TODO Auto-generated method stub
		
	}

}

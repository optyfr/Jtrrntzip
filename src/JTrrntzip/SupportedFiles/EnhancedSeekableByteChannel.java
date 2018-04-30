package JTrrntzip.SupportedFiles;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class EnhancedSeekableByteChannel implements SeekableByteChannel, Closeable, AutoCloseable
{
	private SeekableByteChannel sbc;
	private ByteOrder bo;
	private Checksum checksum = new CRC32();
	
	ByteBuffer lbb = ByteBuffer.allocate(8);
	ByteBuffer ibb = ByteBuffer.allocate(4);
	ByteBuffer sbb = ByteBuffer.allocate(2);
	
	public EnhancedSeekableByteChannel(SeekableByteChannel sbc)
	{
		this.sbc = sbc;
	}
	
	public EnhancedSeekableByteChannel order(ByteOrder bo)
	{
		this.bo = bo;
		lbb.order(this.bo);
		ibb.order(this.bo);
		sbb.order(this.bo);
		return this;
	}

	public ByteOrder order()
	{
		return bo;
	}
	
	public EnhancedSeekableByteChannel put(byte b) throws IOException
	{
		write(ByteBuffer.wrap(new byte[] {b}));
		return this;
	}
	
	public EnhancedSeekableByteChannel put(byte[] b) throws IOException
	{
		write(ByteBuffer.wrap(b));
		return this;
	}

	public EnhancedSeekableByteChannel put(byte[] b, int offset, int len) throws IOException
	{
		write(ByteBuffer.wrap(b, 0, len));
		return this;
	}

	public EnhancedSeekableByteChannel putLong(long l) throws IOException
	{
		lbb.clear();
		lbb.putLong(l);
		write(lbb);
		return this;
	}

	public EnhancedSeekableByteChannel putInt(int i) throws IOException
	{
		ibb.clear();
		ibb.putInt(i);
		write(ibb);
		return this;
	}
	
	public EnhancedSeekableByteChannel putShort(short s) throws IOException
	{
		sbb.clear();
		sbb.putShort(s);
		write(sbb);
		return this;
	}

	public byte get() throws IOException
	{
		ByteBuffer bb = ByteBuffer.allocate(1);
		read(bb);
		checksum.update(bb.get());
		return bb.get();
	}
	
	public EnhancedSeekableByteChannel get(byte[] dst) throws IOException
	{
		ByteBuffer bb = ByteBuffer.allocate(dst.length);
		read(bb);
		bb.get(dst);
		checksum.update(dst, 0, dst.length);
		return this;
	}
	
	public EnhancedSeekableByteChannel get(byte[] dst, int offset, int len) throws IOException
	{
		ByteBuffer bb = ByteBuffer.allocate(len);
		read(bb);
		bb.get(dst, offset, len);
		checksum.update(dst, offset, len);
		return this;
	}
	
	public long getLong() throws IOException
	{
		lbb.clear();
		read(lbb);
		checksum.update(lbb.array(), 0, 8);
		return lbb.getLong();
	}

	public int getInt() throws IOException
	{
		ibb.clear();
		read(ibb);
		checksum.update(ibb.array(), 0, 4);
		return ibb.getInt();
	}

	public short getShort() throws IOException
	{
		sbb.clear();
		read(sbb);
		checksum.update(sbb.array(), 0, 2);
		return sbb.getShort();
	}

	public InputStream getInputStream()
	{
		return Channels.newInputStream(sbc);
	}
	
	public OutputStream getOutputStream()
	{
		return Channels.newOutputStream(sbc);
	}
	
	@Override
	public boolean isOpen()
	{
		return sbc.isOpen();
	}

	@Override
	public void close() throws IOException
	{
		sbc.close();
	}

	@Override
	public int read(ByteBuffer dst) throws IOException
	{
		return sbc.read(dst);
	}

	@Override
	public int write(ByteBuffer src) throws IOException
	{
		return sbc.write(src);
	}

	@Override
	public long position() throws IOException
	{
		return sbc.position();
	}

	@Override
	public SeekableByteChannel position(long newPosition) throws IOException
	{
		return sbc.position(newPosition);
	}

	@Override
	public long size() throws IOException
	{
		return sbc.size();
	}

	@Override
	public SeekableByteChannel truncate(long size) throws IOException
	{
		return sbc.truncate(size);
	}
	
	public long getChecksum()
	{
		return checksum.getValue();
	}

}

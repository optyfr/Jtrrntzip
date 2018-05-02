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

public final class EnhancedSeekableByteChannel implements SeekableByteChannel, Closeable, AutoCloseable
{
	private SeekableByteChannel sbc;
	private ByteOrder bo;
	private Checksum checksum = null;

	private final ByteBuffer lbb = ByteBuffer.allocate(8);
	private final ByteBuffer ibb = ByteBuffer.allocate(4);
	private final ByteBuffer sbb = ByteBuffer.allocate(2);
	
	public EnhancedSeekableByteChannel(SeekableByteChannel sbc, ByteOrder bo)
	{
		this.sbc = sbc;
		order(bo);
	}

	public EnhancedSeekableByteChannel order(ByteOrder bo)
	{
		this.bo = bo;
		lbb.order(this.bo);
		ibb.order(this.bo);
		sbb.order(this.bo);
		return this;
	}

	public final ByteOrder order()
	{
		return bo;
	}

	public final EnhancedSeekableByteChannel put(byte b) throws IOException
	{
		if(checksum!=null)
			checksum.update(b);
		write(ByteBuffer.wrap(new byte[] { b }));
		return this;
	}

	public final EnhancedSeekableByteChannel put(byte[] b) throws IOException
	{
		if(checksum!=null)
			checksum.update(b,0,b.length);
		write(ByteBuffer.wrap(b));
		return this;
	}

	public final EnhancedSeekableByteChannel put(byte[] b, int offset, int len) throws IOException
	{
		if(checksum!=null)
			checksum.update(b,offset,len);
		write(ByteBuffer.wrap(b, 0, len));
		return this;
	}

	public final EnhancedSeekableByteChannel putLong(long l) throws IOException
	{
		if(checksum!=null)
			checksum.update(ByteBuffer.allocate(8).putLong(l).array(),0,8);
		lbb.clear();
		lbb.putLong(l);
		lbb.rewind();
		if(checksum!=null)
			checksum.update(lbb.array(),0,8);
		write(lbb);
		return this;
	}

	public final EnhancedSeekableByteChannel putInt(int i) throws IOException
	{
		ibb.clear();
		ibb.putInt(i);
		ibb.rewind();
		if(checksum!=null)
			checksum.update(ibb.array(),0,4);
		write(ibb);
		return this;
	}

	public final EnhancedSeekableByteChannel putShort(short s) throws IOException
	{
		sbb.clear();
		sbb.putShort(s);
		sbb.rewind();
		if(checksum!=null)
			checksum.update(sbb.array(),0,2);
		write(sbb);
		return this;
	}

	public final byte get() throws IOException
	{
		ByteBuffer bb = ByteBuffer.allocate(1);
		read(bb);
		if(checksum != null)
			checksum.update(bb.get());
		return bb.get();
	}

	public final EnhancedSeekableByteChannel get(byte[] dst) throws IOException
	{
		ByteBuffer bb = ByteBuffer.allocate(dst.length);
		read(bb);
		bb.rewind();
		bb.get(dst);
		if(checksum != null)
			checksum.update(dst, 0, dst.length);
		return this;
	}

	public final EnhancedSeekableByteChannel get(byte[] dst, int offset, int len) throws IOException
	{
		ByteBuffer bb = ByteBuffer.allocate(len);
		read(bb);
		bb.rewind();
		bb.get(dst, offset, len);
		if(checksum != null)
			checksum.update(dst, offset, len);
		return this;
	}

	public final long getLong() throws IOException
	{
		lbb.clear();
		read(lbb);
		if(checksum != null)
			checksum.update(lbb.array(), 0, 8);
		lbb.rewind();
		return lbb.getLong();
	}

	public final int getInt() throws IOException
	{
		ibb.clear();
		read(ibb);
		if(checksum != null)
			checksum.update(ibb.array(), 0, 4);
		ibb.rewind();
		return ibb.getInt();
	}

	public final short getShort() throws IOException
	{
		sbb.clear();
		read(sbb);
		if(checksum != null)
			checksum.update(sbb.array(), 0, 2);
		sbb.rewind();
		return sbb.getShort();
	}

	public final InputStream getInputStream()
	{
		return Channels.newInputStream(sbc);
	}

	public final OutputStream getOutputStream()
	{
		return Channels.newOutputStream(sbc);
	}

	@Override
	public final boolean isOpen()
	{
		return sbc.isOpen();
	}

	@Override
	public final void close() throws IOException
	{
		sbc.close();
	}

	@Override
	public final int read(ByteBuffer dst) throws IOException
	{
		return sbc.read(dst);
	}

	@Override
	public final int write(ByteBuffer src) throws IOException
	{
		return sbc.write(src);
	}

	@Override
	public final long position() throws IOException
	{
		return sbc.position();
	}

	@Override
	public final SeekableByteChannel position(long newPosition) throws IOException
	{
		return sbc.position(newPosition);
	}

	@Override
	public final long size() throws IOException
	{
		return sbc.size();
	}

	@Override
	public final SeekableByteChannel truncate(long size) throws IOException
	{
		return sbc.truncate(size);
	}

	public final void startChecksum()
	{
		checksum = new CRC32();
	}

	public final long endChecksum()
	{
		return checksum.getValue();
	}

}

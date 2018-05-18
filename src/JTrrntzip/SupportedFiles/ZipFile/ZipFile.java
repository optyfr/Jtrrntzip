package JTrrntzip.SupportedFiles.ZipFile;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Checksum;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import JTrrntzip.Messages;
import JTrrntzip.ZipOpenType;
import JTrrntzip.ZipReturn;
import JTrrntzip.ZipStatus;
import JTrrntzip.SupportedFiles.EnhancedSeekableByteChannel;
import JTrrntzip.SupportedFiles.ICompress;
import JTrrntzip.SupportedFiles.UnsignedTypes;

public final class ZipFile implements ICompress
{

	private static final int LocalFileHeaderSignature = 0x04034b50;
	private static final int CentralDirectoryHeaderSignature = 0x02014b50;
	private static final int EndOfCentralDirSignature = 0x06054b50;
	private static final int Zip64EndOfCentralDirSignature = 0x06064b50;
	private static final int Zip64EndOfCentralDirectoryLocator = 0x07064b50;

	public final static class LocalFile implements Closeable, AutoCloseable
	{
		private final EnhancedSeekableByteChannel esbc;

		public String fileName;
		public int _generalPurposeBitFlag;
		private int _compressionMethod;
		private int _lastModFileTime;
		private int _lastModFileDate;
		public byte[] crc;
		private BigInteger _compressedSize;
		public BigInteger _uncompressedSize;
		public BigInteger relativeOffsetOfLocalHeader; // only in centeral directory

		private long _crc32Location;
		private long _extraLocation;
		private long _dataLocation;

		public boolean zip64;
		public boolean _trrntZip;

		public ZipReturn fileStatus = ZipReturn.ZipUntested;

		public LocalFile(EnhancedSeekableByteChannel esbc) throws IOException
		{
			this.esbc = esbc;
		}

		public LocalFile(EnhancedSeekableByteChannel esbc, String filename) throws IOException
		{
			zip64 = false;
			this.esbc = esbc;
			_generalPurposeBitFlag = 2; // Maximum Compression Deflating
			_compressionMethod = 8; // Compression Method Deflate
			_lastModFileTime = 48128;
			_lastModFileDate = 8600;

			fileName = filename;
		}

		public final ZipReturn CentralDirectoryRead()
		{
			try
			{

				int thisSignature = esbc.getInt();
				if(thisSignature != CentralDirectoryHeaderSignature)
					return ZipReturn.ZipCentralDirError;

				esbc.getUShort(); // Version Made By

				esbc.getUShort(); // Version Needed To Extract

				_generalPurposeBitFlag = esbc.getUShort();
				_compressionMethod = esbc.getUShort();
				if(_compressionMethod != 8 && _compressionMethod != 0)
					return ZipReturn.ZipUnsupportedCompression;

				_lastModFileTime = esbc.getUShort();
				_lastModFileDate = esbc.getUShort();
				crc = ReadCRC(esbc);

				_compressedSize = BigInteger.valueOf(esbc.getUInt());
				_uncompressedSize = BigInteger.valueOf(esbc.getUInt());

				int fileNameLength = esbc.getUShort();
				int extraFieldLength = esbc.getUShort();
				int fileCommentLength = esbc.getUShort();

				esbc.getUShort(); // diskNumberStart
				esbc.getUShort(); // internalFileAttributes
				esbc.getUInt(); // externalFileAttributes

				relativeOffsetOfLocalHeader = BigInteger.valueOf(esbc.getUInt());

				byte[] bFileName = new byte[fileNameLength];
				esbc.get(bFileName);
				fileName = (_generalPurposeBitFlag & (1 << 11)) == 0 ? new String(bFileName, Charset.forName("Cp437")) : new String(bFileName, Charset.forName("UTF8")); //$NON-NLS-1$ //$NON-NLS-2$

				byte[] extraField = new byte[extraFieldLength];
				esbc.get(extraField);

				esbc.position(esbc.position() + fileCommentLength); // File Comments

				ByteBuffer bb = ByteBuffer.wrap(extraField).order(ByteOrder.LITTLE_ENDIAN);
				while(extraFieldLength > bb.position())
				{
					int type = UnsignedTypes.toUShort(bb.getShort());
					int blockLength = UnsignedTypes.toUShort(bb.getShort());
					switch(type)
					{
						case 0x0001:
							zip64 = true;
							if(_uncompressedSize.longValue() == 0xffffffffL)
								_uncompressedSize = UnsignedTypes.toULong(bb.getLong());
							if(_compressedSize.longValue() == 0xffffffffL)
								_compressedSize = UnsignedTypes.toULong(bb.getLong());
							if(relativeOffsetOfLocalHeader.longValue() == 0xffffffffL)
								relativeOffsetOfLocalHeader = UnsignedTypes.toULong(bb.getLong());
							break;
						case 0x7075:
							@SuppressWarnings("unused")
							byte version = bb.get();
							long nameCRC32 = UnsignedTypes.toUInt(bb.getInt());

							java.util.zip.CRC32 crcTest = new java.util.zip.CRC32();
							crcTest.update(bFileName);
							long fCRC = crcTest.getValue();

							if(nameCRC32 != fCRC)
								return ZipReturn.ZipCentralDirError;

							int charLen = blockLength - 5;

							byte[] dst = new byte[charLen];
							bb.get(dst);
							fileName = new String(dst, Charset.forName("UTF8")); //$NON-NLS-1$

							break;
						default:
							bb.position(bb.position() + blockLength);
							break;
					}
				}

				return ZipReturn.ZipGood;
			}
			catch(Exception e)
			{
				e.printStackTrace();
				return ZipReturn.ZipCentralDirError;
			}

		}

		public final void CenteralDirectoryWrite(EnhancedSeekableByteChannel esbc) throws IOException
		{

			final int header = 0x02014B50;

			List<Byte> extraField = new ArrayList<>();

			long cdUncompressedSize;
			if(_uncompressedSize.compareTo(BigInteger.valueOf(0xffffffffL)) >= 0)
			{
				zip64 = true;
				cdUncompressedSize = 0xffffffffL;
				for(byte b : ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(UnsignedTypes.fromULong(_uncompressedSize)).array())
					extraField.add(b);
			}
			else
				cdUncompressedSize = UnsignedTypes.fromULong(_uncompressedSize);

			long cdCompressedSize;
			if(_compressedSize.compareTo(BigInteger.valueOf(0xffffffffL)) >= 0)
			{
				zip64 = true;
				cdCompressedSize = 0xffffffffL;
				for(byte b : ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(UnsignedTypes.fromULong(_compressedSize)).array())
					extraField.add(b);
			}
			else
				cdCompressedSize = UnsignedTypes.fromULong(_compressedSize);

			long cdRelativeOffsetOfLocalHeader;
			if(relativeOffsetOfLocalHeader.compareTo(BigInteger.valueOf(0xffffffffL)) >= 0)
			{
				zip64 = true;
				cdRelativeOffsetOfLocalHeader = 0xffffffffL;
				for(byte b : ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(UnsignedTypes.fromULong(relativeOffsetOfLocalHeader)).array())
					extraField.add(b);
			}
			else
				cdRelativeOffsetOfLocalHeader = UnsignedTypes.fromULong(relativeOffsetOfLocalHeader);

			if(extraField.size() > 0)
			{
				int exfl = extraField.size();
				int i = 0;
				for(byte b : ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(UnsignedTypes.fromUShort(0x0001)).array())
					extraField.add(i++, b);
				for(byte b : ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(UnsignedTypes.fromUShort(exfl)).array())
					extraField.add(i++, b);
			}
			int extraFieldLength = extraField.size();

			byte[] bFileName;

			if(!Charset.forName("Cp437").newEncoder().canEncode(fileName)) //$NON-NLS-1$
			{
				_generalPurposeBitFlag |= 1 << 11;
				bFileName = fileName.getBytes(Charset.forName("UTF8")); //$NON-NLS-1$
			}
			else
				bFileName = fileName.getBytes(Charset.forName("Cp437")); //$NON-NLS-1$
			int fileNameLength = bFileName.length;

			int versionNeededToExtract = (zip64 ? 45 : 20);

			esbc.putInt(header);							// 4
			esbc.putUShort(0);								// 6
			esbc.putUShort(versionNeededToExtract); 		// 8
			esbc.putUShort(_generalPurposeBitFlag); 		// 10
			esbc.putUShort(_compressionMethod);				// 12
			esbc.putUShort(_lastModFileTime);				// 14
			esbc.putUShort(_lastModFileDate);				// 16
			esbc.put(crc);									// 20
			esbc.putUInt(cdCompressedSize);					// 24
			esbc.putUInt(cdUncompressedSize);				// 28
			esbc.putUShort(fileNameLength);					// 30
			esbc.putUShort(extraFieldLength);				// 32
			esbc.putUShort(0);								// 34 // file comment length
			esbc.putUShort(0);								// 36 // disk number start
			esbc.putUShort(0);								// 38 // internal file attributes
			esbc.putUInt(0);								// 42 // external file attributes
			esbc.putUInt(cdRelativeOffsetOfLocalHeader);	// 46
			esbc.put(bFileName);
			for(byte b : extraField)
				esbc.put(b);
			// No File Comment
		}

		public final ZipReturn LocalFileHeaderRead()
		{
			try
			{
				_trrntZip = true;

				esbc.position(relativeOffsetOfLocalHeader.longValueExact());
				int thisSignature = esbc.getInt();
				if(thisSignature != LocalFileHeaderSignature)
					return ZipReturn.ZipLocalFileHeaderError;

				esbc.getUShort(); // version needed to extract
				int generalPurposeBitFlagLocal = esbc.getUShort();
				if(generalPurposeBitFlagLocal != _generalPurposeBitFlag)
					_trrntZip = false;

				int tshort = esbc.getUShort();
				if(tshort != _compressionMethod)
					return ZipReturn.ZipLocalFileHeaderError;

				tshort = esbc.getUShort();
				if(tshort != _lastModFileTime)
					return ZipReturn.ZipLocalFileHeaderError;

				tshort = esbc.getUShort();
				if(tshort != _lastModFileDate)
					return ZipReturn.ZipLocalFileHeaderError;

				byte[] tCRC = ReadCRC(esbc);
				if(((_generalPurposeBitFlag & 8) == 0) && !Arrays.equals(tCRC, crc))
					return ZipReturn.ZipLocalFileHeaderError;

				long tCompressedSize = esbc.getUInt();
				if(zip64 && tCompressedSize != 0xffffffffL && tCompressedSize != _compressedSize.longValue()) // if Zip64 File then the compressedSize should be 0xffffffff
					return ZipReturn.ZipLocalFileHeaderError;
				if((_generalPurposeBitFlag & 8) == 8 && tCompressedSize != 0) // if bit 4 set then no compressedSize is set yet
					return ZipReturn.ZipLocalFileHeaderError;
				if(!zip64 && (_generalPurposeBitFlag & 8) != 8 && tCompressedSize != _compressedSize.longValue()) // check the compressedSize
					return ZipReturn.ZipLocalFileHeaderError;

				long tUnCompressedSize = esbc.getUInt();
				if(zip64 && tUnCompressedSize != 0xffffffffL && tUnCompressedSize != _uncompressedSize.longValue()) // if Zip64 File then the unCompressedSize should be 0xffffffff
					return ZipReturn.ZipLocalFileHeaderError;
				if((_generalPurposeBitFlag & 8) == 8 && tUnCompressedSize != 0) // if bit 4 set then no unCompressedSize is set yet
					return ZipReturn.ZipLocalFileHeaderError;
				if(!zip64 && (_generalPurposeBitFlag & 8) != 8 && tUnCompressedSize != _uncompressedSize.longValue()) // check the unCompressedSize
					return ZipReturn.ZipLocalFileHeaderError;

				int fileNameLength = esbc.getUShort();
				int extraFieldLength = esbc.getUShort();

				byte[] bFileName = new byte[fileNameLength];
				esbc.get(bFileName);
				String tFileName = (generalPurposeBitFlagLocal & (1 << 11)) == 0 ? new String(bFileName, Charset.forName("Cp437")) : new String(bFileName, Charset.forName("UTF8")); //$NON-NLS-1$ //$NON-NLS-2$

				byte[] extraField = new byte[extraFieldLength];
				esbc.get(extraField);

				zip64 = false;
				ByteBuffer bb = ByteBuffer.wrap(extraField).order(ByteOrder.LITTLE_ENDIAN);
				while(extraFieldLength > bb.position())
				{
					int type = UnsignedTypes.toUShort(bb.getShort());
					int blockLength = UnsignedTypes.toUShort(bb.getShort());
					switch(type)
					{
						case 0x0001:
							zip64 = true;
							if(tUnCompressedSize == 0xffffffffL)
							{
								BigInteger tLong = UnsignedTypes.toULong(bb.getLong());
								if(tLong.compareTo(_uncompressedSize) != 0)
									return ZipReturn.ZipLocalFileHeaderError;
							}
							if(tCompressedSize == 0xffffffffL)
							{
								BigInteger tLong = UnsignedTypes.toULong(bb.getLong());
								if(tLong.compareTo(_compressedSize) != 0)
									return ZipReturn.ZipLocalFileHeaderError;
							}
							break;
						case 0x7075:
							@SuppressWarnings("unused")
							byte version = bb.get();
							long nameCRC32 = UnsignedTypes.toUInt(bb.getInt());

							java.util.zip.CRC32 crcTest = new java.util.zip.CRC32();
							crcTest.update(bFileName);
							long fCRC = crcTest.getValue();

							if(nameCRC32 != fCRC)
								return ZipReturn.ZipLocalFileHeaderError;

							int charLen = blockLength - 5;

							byte[] dst = new byte[charLen];
							bb.get(dst);
							fileName = new String(dst, Charset.forName("UTF8")); //$NON-NLS-1$

							break;
						default:
							bb.position(bb.position() + blockLength);
							break;
					}
				}

				if(!fileName.equals(tFileName))
					return ZipReturn.ZipLocalFileHeaderError;

				_dataLocation = esbc.position();

				if((_generalPurposeBitFlag & 8) == 0)
					return ZipReturn.ZipGood;

				esbc.position(esbc.position() + _compressedSize.longValue());

				tCRC = ReadCRC(esbc);
				if(!Arrays.equals(tCRC, new byte[] { 0x50, 0x4b, 0x07, 0x08 }))
					tCRC = ReadCRC(esbc);

				if(!Arrays.equals(tCRC, crc))
					return ZipReturn.ZipLocalFileHeaderError;

				long tint = esbc.getUInt();
				if(tint != _compressedSize.longValue())
					return ZipReturn.ZipLocalFileHeaderError;

				tint = esbc.getUInt();
				if(tint != _uncompressedSize.longValue())
					return ZipReturn.ZipLocalFileHeaderError;

				return ZipReturn.ZipGood;
			}
			catch(Exception e)
			{
				e.printStackTrace();
				return ZipReturn.ZipLocalFileHeaderError;
			}

		}

		public final ZipReturn LocalFileHeaderReadQuick()
		{
			try
			{
				_trrntZip = true;

				esbc.position(relativeOffsetOfLocalHeader.longValue());
				int thisSignature = esbc.getInt();
				if(thisSignature != LocalFileHeaderSignature)
					return ZipReturn.ZipLocalFileHeaderError;

				esbc.getShort(); // version needed to extract
				_generalPurposeBitFlag = esbc.getUShort();
				if((_generalPurposeBitFlag & 8) == 8)
					return ZipReturn.ZipCannotFastOpen;

				_compressionMethod = esbc.getUShort();
				_lastModFileTime = esbc.getUShort();
				_lastModFileDate = esbc.getUShort();
				crc = ReadCRC(esbc);
				_compressedSize = BigInteger.valueOf(esbc.getUInt());
				_uncompressedSize = BigInteger.valueOf(esbc.getUInt());

				int fileNameLength = esbc.getUShort();
				int extraFieldLength = esbc.getUShort();

				byte[] bFileName = new byte[fileNameLength];
				esbc.get(bFileName);
				@SuppressWarnings("unused")
				String tFileName = (_generalPurposeBitFlag & (1 << 11)) == 0 ? new String(bFileName, Charset.forName("Cp437")) : new String(bFileName, Charset.forName("UTF8")); //$NON-NLS-1$ //$NON-NLS-2$

				byte[] extraField = new byte[extraFieldLength];
				esbc.get(extraField);

				zip64 = false;
				ByteBuffer bb = ByteBuffer.wrap(extraField).order(ByteOrder.LITTLE_ENDIAN);
				while(extraFieldLength > bb.position())
				{
					int type = UnsignedTypes.toUShort(bb.getShort());
					int blockLength = UnsignedTypes.toUShort(bb.getShort());
					switch(type)
					{
						case 0x0001:
							zip64 = true;
							if(_uncompressedSize.compareTo(BigInteger.valueOf(0xffffffffL)) == 0)
								_uncompressedSize = UnsignedTypes.toULong(bb.getLong());
							if(_compressedSize.compareTo(BigInteger.valueOf(0xffffffffL)) == 0)
								_compressedSize = UnsignedTypes.toULong(bb.getLong());
							break;
						case 0x7075:
							@SuppressWarnings("unused")
							byte version = bb.get();
							long nameCRC32 = UnsignedTypes.toUInt(bb.getInt());

							java.util.zip.CRC32 crcTest = new java.util.zip.CRC32();
							crcTest.update(bFileName);
							long fCRC = crcTest.getValue();

							if(nameCRC32 != fCRC)
								return ZipReturn.ZipLocalFileHeaderError;

							int charLen = blockLength - 5;

							byte[] dst = new byte[charLen];
							bb.get(dst);
							fileName = new String(dst, Charset.forName("UTF8")); //$NON-NLS-1$
							break;
						default:
							bb.position(bb.position() + blockLength);
							break;
					}
				}

				_dataLocation = esbc.position();
				return ZipReturn.ZipGood;

			}
			catch(Exception e)
			{
				e.printStackTrace();
				return ZipReturn.ZipLocalFileHeaderError;
			}

		}

		private final void LocalFileHeaderWrite() throws IOException
		{
			List<Byte> extraField = new ArrayList<>();
			zip64 = _uncompressedSize.compareTo(BigInteger.valueOf(0xffffffffL)) >= 0;
			if(zip64)
				new Exception().printStackTrace();

			byte[] bFileName;

			if(!Charset.forName("Cp437").newEncoder().canEncode(fileName)) //$NON-NLS-1$
			{
				_generalPurposeBitFlag |= 1 << 11;
				bFileName = fileName.getBytes(Charset.forName("UTF8")); //$NON-NLS-1$
			}
			else
				bFileName = fileName.getBytes(Charset.forName("Cp437")); //$NON-NLS-1$

			int versionNeededToExtract = (zip64 ? 45 : 20);

			relativeOffsetOfLocalHeader = BigInteger.valueOf(esbc.position());
			final int header = 0x4034B50;

			esbc.putUInt(header); // 4
			esbc.putUShort(versionNeededToExtract); // 8
			esbc.putUShort(_generalPurposeBitFlag); // 10
			esbc.putUShort(_compressionMethod); // 12
			esbc.putUShort(_lastModFileTime); // 14
			esbc.putUShort(_lastModFileDate); // 16

			_crc32Location = esbc.position();

			// these 3 values will be set correctly after the file data has been written
			esbc.putUInt(0xFFFFFFFFL);
			esbc.putUInt(0xFFFFFFFFL);
			esbc.putUInt(0xFFFFFFFFL);

			if(zip64)
			{
				for(int i = 0; i < 20; i++)
					extraField.add((byte) 0);
			}

			int fileNameLength = bFileName.length;
			esbc.putUShort(fileNameLength);

			int extraFieldLength = extraField.size();
			esbc.putUShort(extraFieldLength);

			esbc.put(bFileName);

			_extraLocation = esbc.position();
			for(byte b : extraField)
				esbc.put(b);
		}

		private InputStream _readStream;

		public final ZipReturn LocalFileOpenReadStream(boolean raw, AtomicReference<InputStream> stream, AtomicReference<BigInteger> streamSize, AtomicInteger compressionMethod) throws IOException
		{
			streamSize.set(BigInteger.valueOf(0));
			compressionMethod.set(_compressionMethod);

			_readStream = null;
			esbc.position(_dataLocation);

			switch(_compressionMethod)
			{
				case 8:
					if(raw)
					{
						_readStream = esbc.getInputStream();
						streamSize.set(_compressedSize);
					}
					else
					{
						_readStream = new InflaterInputStream(esbc.getInputStream(), new Inflater(true));
						streamSize.set(_uncompressedSize);

					}
					break;
				case 0:
					_readStream = esbc.getInputStream();
					streamSize.set(_compressedSize); // same as UncompressedSize
					break;
			}
			stream.set(_readStream);
			return stream.get() == null ? ZipReturn.ZipErrorGettingDataStream : ZipReturn.ZipGood;
		}

		public final ZipReturn LocalFileCloseReadStream() throws IOException
		{
			InputStream dfStream = _readStream;
			if(dfStream != null)
			{
				// dfStream.close();
			}
			// close();
			return ZipReturn.ZipGood;
		}

		private OutputStream _writeStream;

		public final ZipReturn LocalFileOpenWriteStream(boolean raw, boolean trrntZip, BigInteger uncompressedSize, int compressionMethod, AtomicReference<OutputStream> stream) throws IOException
		{
			_uncompressedSize = uncompressedSize;
			_compressionMethod = compressionMethod;

			LocalFileHeaderWrite();
			_dataLocation = esbc.position();

			if(raw)
			{
				_writeStream = esbc.getOutputStream();
				_trrntZip = trrntZip;
			}
			else
			{
				if(compressionMethod == 0)
				{
					_writeStream = esbc.getOutputStream();
					_trrntZip = false;
				}
				else
				{
					_writeStream = new DeflaterOutputStream(esbc.getOutputStream(), new Deflater(9, true), false);
					_trrntZip = true;
				}
			}

			stream.set(_writeStream);
			return stream.get() == null ? ZipReturn.ZipErrorGettingDataStream : ZipReturn.ZipGood;
		}

		public final ZipReturn LocalFileCloseWriteStream(byte[] crc32) throws IOException
		{
			OutputStream dfStream = _writeStream;
			if(dfStream != null)
			{
				dfStream.flush();
				// dfStream.close();
			}

			_compressedSize = BigInteger.valueOf(esbc.position() - _dataLocation);

			if(_compressedSize.longValue() == 0x0L && _uncompressedSize.longValue() == 0x0L)
			{
				LocalFileAddDirectory();
				_compressedSize = BigInteger.valueOf(esbc.position() - _dataLocation);
			}

			crc = crc32;
			WriteCompressedSize();
			// close();
			return ZipReturn.ZipGood;
		}

		private final void WriteCompressedSize() throws IOException
		{
			long posNow = esbc.position();

			esbc.position(_crc32Location);

			long tCompressedSize;
			long tUncompressedSize;
			if(zip64)
			{
				tCompressedSize = 0xffffffffL;
				tUncompressedSize = 0xffffffffL;
			}
			else
			{
				tCompressedSize = _compressedSize.longValue();
				tUncompressedSize = _uncompressedSize.longValue();
			}

			esbc.put(crc);
			esbc.putUInt(tCompressedSize);
			esbc.putUInt(tUncompressedSize);

			// also need to write extradata
			if(zip64)
			{
				esbc.position(_extraLocation);
				esbc.putUShort(0x0001); // id
				esbc.putUShort(16); // data length
				esbc.putULong(_uncompressedSize);
				esbc.putULong(_compressedSize);
			}

			esbc.position(posNow);

		}

		public final void LocalFileCheck()
		{
			if(fileStatus != ZipReturn.ZipUntested)
				return;

			try
			{
				InputStream sInput = null;
				esbc.position(_dataLocation);

				switch(_compressionMethod)
				{
					case 8:
						sInput = new InflaterInputStream(esbc.getInputStream(), new Inflater(true));
						break;
					case 0:
						sInput = esbc.getInputStream();
						break;
				}

				if(sInput == null)
				{
					fileStatus = ZipReturn.ZipErrorGettingDataStream;
					return;
				}

				final int Buffersize = 8 * 1024;
				byte[] buffer = new byte[Buffersize];
				BigInteger sizetogo = _uncompressedSize;
				Checksum tcrc32 = new java.util.zip.CRC32();

				// Pre load the first buffer0
				int sizeNext = sizetogo.compareTo(BigInteger.valueOf(Buffersize)) > 0 ? Buffersize : sizetogo.intValue();
				int sizebuffer = sInput.read(buffer, 0, sizeNext);
				sizetogo = sizetogo.subtract(BigInteger.valueOf(sizebuffer));

				while(sizebuffer > 0)
				{
					sizeNext = sizetogo.compareTo(BigInteger.valueOf(Buffersize)) > 0 ? Buffersize : sizetogo.intValue();

					if(sizeNext > 0)
					{
						sizebuffer = sInput.read(buffer, 0, sizeNext);

						if(sizebuffer > 0)
						{
							tcrc32.update(buffer, 0, sizebuffer);
							sizetogo = sizetogo.subtract(BigInteger.valueOf(sizebuffer));
						}
					}
				}

				if(sizetogo.longValue() > 0)
				{
					fileStatus = ZipReturn.ZipDecodeError;
					return;
				}

				byte[] testcrc = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(UnsignedTypes.fromUInt(tcrc32.getValue())).array();

				fileStatus = Arrays.equals(crc, testcrc) ? ZipReturn.ZipGood : ZipReturn.ZipCRCDecodeError;
			}
			catch(Exception e)
			{
				fileStatus = ZipReturn.ZipDecodeError;
			}
		}

		public final void LocalFileAddDirectory() throws IOException
		{
			esbc.put((byte) 3);
			esbc.put((byte) 0);
		}

		public final BigInteger LocalFilePos()
		{
			return relativeOffsetOfLocalHeader;
		}

		public final void LocalFilePos(BigInteger value)
		{
			relativeOffsetOfLocalHeader = value;
		}

		private final byte[] ReadCRC(EnhancedSeekableByteChannel esbc) throws IOException
		{
			return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(esbc.getInt()).array();
		}

		@Override
		public final void close() throws IOException
		{
			if(this.esbc != null)
				this.esbc.close();
		}

	}

	private File _zipFileInfo = null;

	public final String ZipFilename()
	{
		return _zipFileInfo != null ? _zipFileInfo.getAbsolutePath() : ""; //$NON-NLS-1$
	}

	public final long TimeStamp()
	{
		return _zipFileInfo != null ? _zipFileInfo.lastModified() : 0;
	}

	private BigInteger _centerDirStart;
	private BigInteger _centerDirSize;
	private BigInteger _endOfCenterDir64;

	byte[] _fileComment;
	private EnhancedSeekableByteChannel _esbc;

	private BigInteger _localFilesCount;
	private final List<LocalFile> _localFiles = new ArrayList<>();

	private EnumSet<ZipStatus> _pZipStatus = EnumSet.noneOf(ZipStatus.class);
	private boolean _zip64;
	private ZipOpenType zipOpen = ZipOpenType.Closed;

	public final ZipOpenType ZipOpen()
	{
		return zipOpen;
	}

	public final EnumSet<ZipStatus> ZipStatus()
	{
		return _pZipStatus;
	}

	public final int LocalFilesCount()
	{
		return _localFiles.size();
	}

	public final String Filename(int i)
	{
		return _localFiles.get(i).fileName;
	}

	public final BigInteger UncompressedSize(int i)
	{
		return _localFiles.get(i)._uncompressedSize;
	}

	public final BigInteger LocalHeader(int i)
	{
		return ((_localFiles.get(i)._generalPurposeBitFlag & 8) == 0) ? _localFiles.get(i).relativeOffsetOfLocalHeader : null;
	}

	public final ZipReturn FileStatus(int i)
	{
		return _localFiles.get(i).fileStatus;
	}

	public final byte[] CRC32(int i)
	{
		return _localFiles.get(i).crc;
	}

	@Override
	public final void close()
	{
		if(_esbc != null)
		{
			try
			{
				_esbc.close();
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	private final ZipReturn FindEndOfCentralDirSignature() throws IOException
	{
		long fileSize = _esbc.size();

		long maxBackSearch = 0xffffL;

		if(_esbc.size() < maxBackSearch)
			maxBackSearch = fileSize;

		final int buffSize = 0x400;

		byte[] buffer = new byte[buffSize + 4];

		long backPosition = 4;
		while(backPosition < maxBackSearch)
		{
			backPosition += buffSize;
			if(backPosition > maxBackSearch)
				backPosition = maxBackSearch;

			long readSize = backPosition > (buffSize + 4) ? (buffSize + 4) : backPosition;

			_esbc.position(fileSize - backPosition);

			_esbc.get(buffer, 0, (int) readSize);

			for(int i = (int) readSize - 4; i >= 0; i--)
			{
				if((buffer[i] != 0x50) || (buffer[i + 1] != 0x4b) || (buffer[i + 2] != 0x05) || (buffer[i + 3] != 0x06))
					continue;

				_esbc.position((fileSize - backPosition) + i);
				return ZipReturn.ZipGood;
			}
		}
		return ZipReturn.ZipCentralDirError;
	}

	private final ZipReturn EndOfCentralDirRead() throws IOException
	{
		long thisSignature = _esbc.getInt();
		if(thisSignature != EndOfCentralDirSignature)
			return ZipReturn.ZipEndOfCentralDirectoryError;

		int tushort = _esbc.getUShort(); // NumberOfThisDisk
		if(tushort != 0)
			return ZipReturn.ZipEndOfCentralDirectoryError;

		tushort = _esbc.getUShort(); // NumberOfThisDiskCenterDir
		if(tushort != 0)
			return ZipReturn.ZipEndOfCentralDirectoryError;

		_localFilesCount = BigInteger.valueOf(_esbc.getUShort()); // TotalNumberOfEnteriesDisk

		tushort = _esbc.getUShort(); // TotalNumber of enteries in the central directory
		if(BigInteger.valueOf(tushort).compareTo(_localFilesCount) != 0)
			return ZipReturn.ZipEndOfCentralDirectoryError;

		_centerDirSize = BigInteger.valueOf(_esbc.getUInt()); // SizeOfCenteralDir
		_centerDirStart = BigInteger.valueOf(_esbc.getUInt()); // Offset

		int zipFileCommentLength = _esbc.getUShort();

		_fileComment = new byte[zipFileCommentLength];
		_esbc.get(_fileComment);

		if(_esbc.position() != _esbc.size())
			_pZipStatus.add(ZipStatus.ExtraData);

		return ZipReturn.ZipGood;
	}

	private final void EndOfCentralDirWrite() throws IOException
	{
		_esbc.putUInt(EndOfCentralDirSignature);
		_esbc.putUShort(0); // NumberOfThisDisk
		_esbc.putUShort(0); // NumberOfThisDiskCenterDir
		_esbc.putUShort((_localFiles.size() >= 0xffff ? 0xffff : _localFiles.size())); // TotalNumberOfEnteriesDisk
		_esbc.putUShort((_localFiles.size() >= 0xffff ? 0xffff : _localFiles.size())); // TotalNumber of enteries in the central directory
		_esbc.putUInt((_centerDirSize.compareTo(BigInteger.valueOf(0xffffffffL)) >= 0 ? 0xffffffffL : _centerDirSize).longValue());
		_esbc.putUInt((_centerDirStart.compareTo(BigInteger.valueOf(0xffffffffL)) >= 0 ? 0xffffffffL : _centerDirStart).longValue());
		_esbc.putUShort(_fileComment.length);
		_esbc.put(_fileComment, 0, _fileComment.length);
	}

	private final ZipReturn Zip64EndOfCentralDirRead() throws IOException
	{
		_zip64 = true;

		long thisSignature = _esbc.getInt();
		if(thisSignature != Zip64EndOfCentralDirSignature)
			return ZipReturn.ZipEndOfCentralDirectoryError;

		BigInteger tulong = _esbc.getULong(); // Size of zip64 end of central directory record
		if(tulong.compareTo(BigInteger.valueOf(44)) != 0)
			return ZipReturn.Zip64EndOfCentralDirError;

		_esbc.getShort(); // version made by

		int tushort = _esbc.getUShort(); // version needed to extract
		if(tushort != 45)
			return ZipReturn.Zip64EndOfCentralDirError;

		long tuint = _esbc.getUInt(); // number of this disk
		if(tuint != 0)
			return ZipReturn.Zip64EndOfCentralDirError;

		tuint = _esbc.getUInt(); // number of the disk with the start of the central directory
		if(tuint != 0)
			return ZipReturn.Zip64EndOfCentralDirError;

		_localFilesCount = _esbc.getULong(); // total number of entries in the central directory on this disk

		tulong = _esbc.getULong(); // total number of entries in the central directory
		if(tulong != _localFilesCount)
			return ZipReturn.Zip64EndOfCentralDirError;

		_centerDirSize = _esbc.getULong(); // size of central directory

		_centerDirStart = _esbc.getULong(); // offset of start of central directory with respect to the starting disk number

		return ZipReturn.ZipGood;
	}

	private final void Zip64EndOfCentralDirWrite() throws IOException
	{
		_esbc.putInt(Zip64EndOfCentralDirSignature);
		_esbc.putULong(BigInteger.valueOf(44L)); // Size of zip64 end of central directory record
		_esbc.putUShort(45); // version made by
		_esbc.putUShort(45); // version needed to extract
		_esbc.putUInt(0); // number of this disk
		_esbc.putUInt(0); // number of the disk with the start of the central directroy
		_esbc.putULong(BigInteger.valueOf(_localFiles.size())); // total number of entries in the central directory on this disk
		_esbc.putULong(BigInteger.valueOf(_localFiles.size())); // total number of entries in the central directory
		_esbc.putULong(_centerDirSize); // size of central directory
		_esbc.putULong(_centerDirStart); // offset of start of central directory with respect to the starting disk number
	}

	private final ZipReturn Zip64EndOfCentralDirectoryLocatorRead() throws IOException
	{
		_zip64 = true;

		long thisSignature = _esbc.getUInt();
		if(thisSignature != Zip64EndOfCentralDirectoryLocator)
			return ZipReturn.ZipEndOfCentralDirectoryError;

		long tuint = _esbc.getUInt(); // number of the disk with the start of the zip64 end of centeral directory
		if(tuint != 0)
			return ZipReturn.Zip64EndOfCentralDirectoryLocatorError;

		_endOfCenterDir64 = _esbc.getULong(); // relative offset of the zip64 end of central directroy record

		tuint = _esbc.getUInt(); // total number of disks
		if(tuint != 1)
			return ZipReturn.Zip64EndOfCentralDirectoryLocatorError;

		return ZipReturn.ZipGood;
	}

	private final void Zip64EndOfCentralDirectoryLocatorWrite() throws IOException
	{
		_esbc.putInt(Zip64EndOfCentralDirectoryLocator);
		_esbc.putUInt(0); // number of the disk with the start of the zip64 end of centeral directory
		_esbc.putULong(_endOfCenterDir64); // relative offset of the zip64 end of central directroy record
		_esbc.putUInt(1); // total number of disks
	}

	public final ZipReturn ZipFileOpen(File newFilename, long timestamp, boolean readHeaders) throws IOException
	{
		ZipFileClose();
		_pZipStatus = EnumSet.noneOf(ZipStatus.class);
		_zip64 = false;
		_centerDirStart = BigInteger.valueOf(0);
		_centerDirSize = BigInteger.valueOf(0);
		_zipFileInfo = null;

		try
		{
			if(!newFilename.exists())
			{
				ZipFileClose();
				return ZipReturn.ZipErrorFileNotFound;
			}
			_zipFileInfo = newFilename;
			if(_zipFileInfo.lastModified() != timestamp)
			{
				ZipFileClose();
				return ZipReturn.ZipErrorTimeStamp;
			}
			_esbc = new EnhancedSeekableByteChannel(Files.newByteChannel(newFilename.toPath(), StandardOpenOption.READ), ByteOrder.LITTLE_ENDIAN);
		}
		catch(IOException e)
		{
			ZipFileClose();
			return ZipReturn.ZipErrorOpeningFile;
		}
		zipOpen = ZipOpenType.OpenRead;

		if(!readHeaders)
			return ZipReturn.ZipGood;

		try
		{
			ZipReturn zRet = FindEndOfCentralDirSignature();
			if(zRet != ZipReturn.ZipGood)
			{
				ZipFileClose();
				return zRet;
			}

			long endOfCentralDir = _esbc.position();
			zRet = EndOfCentralDirRead();
			if(zRet != ZipReturn.ZipGood)
			{
				ZipFileClose();
				return zRet;
			}

			// check if this is a ZIP64 zip and if it is read the Zip64 End Of Central Dir Info
			if(_centerDirStart.compareTo(BigInteger.valueOf(0xffffffffL)) == 0 || _centerDirSize.compareTo(BigInteger.valueOf(0xffffffffL)) == 0 || _localFilesCount.compareTo(BigInteger.valueOf(0xffff)) == 0)
			{
				_zip64 = true;
				_esbc.position(endOfCentralDir - 20);
				zRet = Zip64EndOfCentralDirectoryLocatorRead();
				if(zRet != ZipReturn.ZipGood)
				{
					ZipFileClose();
					return zRet;
				}
				_esbc.position(_endOfCenterDir64.longValue());
				zRet = Zip64EndOfCentralDirRead();
				if(zRet != ZipReturn.ZipGood)
				{
					ZipFileClose();
					return zRet;
				}
			}

			boolean trrntzip = false;

			// check if the ZIP has a valid TorrentZip file comment
			if(_fileComment.length == 22)
			{
				if(new String(_fileComment, Charset.forName("Cp437")).substring(0, 14).equals("TORRENTZIPPED-")) //$NON-NLS-1$ //$NON-NLS-2$
				{
					byte[] buffer = new byte[_centerDirSize.intValue()];
					_esbc.position(_centerDirStart.longValue());
					_esbc.startChecksum();
					_esbc.get(buffer);
					long r = _esbc.endChecksum();

					String tcrc = new String(_fileComment, Charset.forName("Cp437")).substring(14, 22); //$NON-NLS-1$
					String zcrc = String.format("%08X", r); //$NON-NLS-1$
					if(tcrc.equalsIgnoreCase(zcrc))
						trrntzip = true;

				}
			}

			// now read the central directory
			_esbc.position(_centerDirStart.longValue());

			_localFiles.clear();
			for(long i = 0; i < _localFilesCount.longValue(); i++)
			{
				LocalFile lc = new LocalFile(_esbc);
				zRet = lc.CentralDirectoryRead();
				if(zRet != ZipReturn.ZipGood)
				{
					ZipFileClose();
					lc.close();
					return zRet;
				}
				_zip64 |= lc.zip64;
				_localFiles.add(lc);
			}

			for(int i = 0; i < _localFilesCount.intValue(); i++)
			{
				zRet = _localFiles.get(i).LocalFileHeaderRead();
				if(zRet != ZipReturn.ZipGood)
				{
					ZipFileClose();
					return zRet;
				}
				trrntzip &= _localFiles.get(i)._trrntZip;
			}

			// check trrntzip file order
			if(trrntzip)
				for(int i = 0; i < _localFilesCount.intValue() - 1; i++)
				{
					if(_localFiles.get(i).fileName.compareToIgnoreCase(_localFiles.get(i + 1).fileName) < 0)
						continue;
					trrntzip = false;
					break;
				}

			// check trrntzip directories
			if(trrntzip)
				for(int i = 0; i < _localFilesCount.intValue() - 1; i++)
				{
					// see if we found a directory
					String filename0 = _localFiles.get(i).fileName;
					if(filename0.charAt(filename0.length() - 1) != '/')
						continue;

					// see if the next file is in that directory
					String filename1 = _localFiles.get(i + 1).fileName;
					if(filename1.length() <= filename0.length())
						continue;
					if(filename0.compareToIgnoreCase(filename1.substring(0, filename0.length())) != 0)
						continue;

					// if we found a file in the directory then we do not need the directory entry
					trrntzip = false;
					break;
				}

			if(trrntzip)
				_pZipStatus.add(ZipStatus.TrrntZip);

			return ZipReturn.ZipGood;
		}
		catch(Exception e)
		{
			e.printStackTrace();
			ZipFileClose();
			return ZipReturn.ZipErrorReadingFile;
		}

	}

	public final ZipReturn ZipFileCreate(File newFilename) throws IOException
	{
		if(zipOpen != ZipOpenType.Closed)
			return ZipReturn.ZipFileAlreadyOpen;

		CreateDirForFile(newFilename);
		_zipFileInfo = newFilename;

		_esbc = new EnhancedSeekableByteChannel(Files.newByteChannel(newFilename.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ), ByteOrder.LITTLE_ENDIAN);
		zipOpen = ZipOpenType.OpenWrite;
		return ZipReturn.ZipGood;
	}

	public final void ZipFileClose() throws IOException
	{
		if(zipOpen == ZipOpenType.Closed)
			return;

		if(zipOpen == ZipOpenType.OpenRead)
		{
			close();
			zipOpen = ZipOpenType.Closed;
			return;
		}

		_zip64 = false;
		boolean lTrrntzip = true;

		_centerDirStart = BigInteger.valueOf(_esbc.position());
		if(_centerDirStart.compareTo(BigInteger.valueOf(0xffffffffL)) >= 0)
			_zip64 = true;

		_esbc.startChecksum();
		for(LocalFile t : _localFiles)
		{
			t.CenteralDirectoryWrite(_esbc);
			_zip64 |= t.zip64;
			lTrrntzip &= t._trrntZip;
		}

		_centerDirSize = BigInteger.valueOf(_esbc.position() - _centerDirStart.longValue());

		_fileComment = lTrrntzip ? String.format("TORRENTZIPPED-%08X", _esbc.endChecksum()).getBytes(Charset.forName("Cp437")) : new byte[0]; //$NON-NLS-1$ //$NON-NLS-2$
		_pZipStatus = lTrrntzip ? EnumSet.of(ZipStatus.TrrntZip) : EnumSet.noneOf(ZipStatus.class);

		if(_zip64)
		{
			_endOfCenterDir64 = BigInteger.valueOf(_esbc.position());
			Zip64EndOfCentralDirWrite();
			Zip64EndOfCentralDirectoryLocatorWrite();
		}
		EndOfCentralDirWrite();

		_esbc.truncate(_esbc.position());
		close();
		zipOpen = ZipOpenType.Closed;

	}

	public final void ZipFileCloseFailed() throws IOException
	{
		if(zipOpen == ZipOpenType.Closed)
			return;

		if(zipOpen == ZipOpenType.OpenRead)
		{
			close();
			zipOpen = ZipOpenType.Closed;
			return;
		}

		close();
		_zipFileInfo.delete();
		_zipFileInfo = null;
		zipOpen = ZipOpenType.Closed;
	}

	private int _readIndex;

	public final ZipReturn ZipFileOpenReadStream(int index, boolean raw, AtomicReference<InputStream> stream, AtomicReference<BigInteger> streamSize, AtomicInteger compressionMethod) throws IOException
	{
		streamSize.set(BigInteger.valueOf(0));
		compressionMethod.set(0);
		_readIndex = index;
		stream.set(null);
		if(zipOpen != ZipOpenType.OpenRead)
			return ZipReturn.ZipReadingFromOutputFile;

		ZipReturn zRet = _localFiles.get(index).LocalFileHeaderRead();
		if(zRet != ZipReturn.ZipGood)
		{
			ZipFileClose();
			return zRet;
		}

		return _localFiles.get(index).LocalFileOpenReadStream(raw, stream, streamSize, compressionMethod);
	}

	public final ZipReturn ZipFileOpenReadStreamQuick(BigInteger pos, boolean raw, AtomicReference<InputStream> stream, AtomicReference<BigInteger> streamSize, AtomicInteger compressionMethod) throws IOException
	{
		LocalFile tmpFile = new LocalFile(_esbc);
		tmpFile.LocalFilePos(pos);

		_localFiles.clear();
		_localFiles.add(tmpFile);
		ZipReturn zr = tmpFile.LocalFileHeaderReadQuick();
		if(zr != ZipReturn.ZipGood)
		{
			stream.set(null);
			streamSize.set(BigInteger.valueOf(0));
			compressionMethod.set(0);
			return zr;
		}
		_readIndex = 0;

		return tmpFile.LocalFileOpenReadStream(raw, stream, streamSize, compressionMethod);
	}

	public final ZipReturn ZipFileCloseReadStream() throws IOException
	{
		return _localFiles.get(_readIndex).LocalFileCloseReadStream();
	}

	public final ZipReturn ZipFileOpenWriteStream(boolean raw, boolean trrntzip, String filename, BigInteger uncompressedSize, short compressionMethod, AtomicReference<OutputStream> stream) throws IOException
	{
		stream.set(null);
		if(zipOpen != ZipOpenType.OpenWrite)
			return ZipReturn.ZipWritingToInputFile;

		LocalFile lf = new LocalFile(_esbc, filename);

		ZipReturn retVal = lf.LocalFileOpenWriteStream(raw, trrntzip, uncompressedSize, compressionMethod, stream);

		_localFiles.add(lf);

		return retVal;
	}

	public final ZipReturn ZipFileCloseWriteStream(byte[] crc32) throws IOException
	{
		return _localFiles.get(_localFiles.size() - 1).LocalFileCloseWriteStream(crc32);
	}

	public final ZipReturn ZipFileRollBack() throws IOException
	{
		if(zipOpen != ZipOpenType.OpenWrite)
			return ZipReturn.ZipWritingToInputFile;

		int fileCount = _localFiles.size();
		if(fileCount == 0)
			return ZipReturn.ZipErrorRollBackFile;

		LocalFile lf = _localFiles.get(fileCount - 1);

		_localFiles.remove(fileCount - 1);
		_esbc.position(lf.LocalFilePos().longValueExact());
		return ZipReturn.ZipGood;
	}

	public final void ZipFileAddDirectory() throws IOException
	{
		_localFiles.get(_localFiles.size() - 1).LocalFileAddDirectory();
	}

	/*
	 * public void BreakTrrntZip(string filename) { _zipFs = new FileStream(filename, FileMode.Open, FileAccess.ReadWrite); BinaryReader zipBr = new BinaryReader(_zipFs); _zipFs.Position = _zipFs.Length - 22; byte[] fileComment =
	 * zipBr.ReadBytes(22); if (GetString(fileComment).Substring(0, 14) == "TORRENTZIPPED-") { _zipFs.Position = _zipFs.Length - 8; _zipFs.WriteByte(48); _zipFs.WriteByte(48); _zipFs.WriteByte(48); _zipFs.WriteByte(48);
	 * _zipFs.WriteByte(48); _zipFs.WriteByte(48); _zipFs.WriteByte(48); _zipFs.WriteByte(48); }
	 * 
	 * zipBr.Close(); _zipFs.Flush(); _zipFs.Close(); }
	 */

	public final void DeepScan()
	{
		for(LocalFile lfile : _localFiles)
			lfile.LocalFileCheck();
	}

	private final static void CreateDirForFile(File sFilename)
	{
		sFilename.getParentFile().mkdirs();
	}

	public final static String ZipErrorMessageText(ZipReturn zS)
	{
		String ret = Messages.getString("ZipFile.ZIPUnknown"); //$NON-NLS-1$
		switch(zS)
		{
			case ZipGood:
				ret = Messages.getString("ZipFile.ZIPGood"); //$NON-NLS-1$
				break;
			case ZipFileCountError:
				ret = Messages.getString("ZipFile.ZIPFileCountError"); //$NON-NLS-1$
				break;
			case ZipSignatureError:
				ret = Messages.getString("ZipFile.ZipSignatureError"); //$NON-NLS-1$
				break;
			case ZipExtraDataOnEndOfZip:
				ret = Messages.getString("ZipFile.ZipExtraDataOnEndOfZip"); //$NON-NLS-1$
				break;
			case ZipUnsupportedCompression:
				ret = Messages.getString("ZipFile.ZipUnsipportedCompression"); //$NON-NLS-1$
				break;
			case ZipLocalFileHeaderError:
				ret = Messages.getString("ZipFile.ZipLocalFileHeaderError"); //$NON-NLS-1$
				break;
			case ZipCentralDirError:
				ret = Messages.getString("ZipFile.ZipCentralDirError"); //$NON-NLS-1$
				break;
			case ZipReadingFromOutputFile:
				ret = Messages.getString("ZipFile.ZipReadingFromOutputFile"); //$NON-NLS-1$
				break;
			case ZipWritingToInputFile:
				ret = Messages.getString("ZipFile.ZipWritingToInputFile"); //$NON-NLS-1$
				break;
			case ZipErrorGettingDataStream:
				ret = Messages.getString("ZipFile.ZipErrorGettingDataStream"); //$NON-NLS-1$
				break;
			case ZipCRCDecodeError:
				ret = Messages.getString("ZipFile.ZipCRCDecodeError"); //$NON-NLS-1$
				break;
			case ZipDecodeError:
				ret = Messages.getString("ZipFile.ZipDecodeError"); //$NON-NLS-1$
				break;
			default:
				ret = zS.toString();
				break;
		}

		return ret;
	}

}

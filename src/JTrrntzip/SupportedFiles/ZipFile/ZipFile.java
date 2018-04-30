package JTrrntzip.SupportedFiles.ZipFile;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import JTrrntzip.ZipOpenType;
import JTrrntzip.ZipReturn;
import JTrrntzip.ZipStatus;
import JTrrntzip.SupportedFiles.EnhancedSeekableByteChannel;
import JTrrntzip.SupportedFiles.ICompress;

public class ZipFile implements ICompress, AutoCloseable, Closeable
{
	final int Buffersize = 4096 * 1024;
	private static byte[] _buffer0;

	private final int LocalFileHeaderSignature = 0x04034b50;
	private final int CentralDirectoryHeaderSignature = 0x02014b50;
	private final int EndOfCentralDirSignature = 0x06054b50;
	private final int Zip64EndOfCentralDirSignature = 0x06064b50;
	private final int Zip64EndOfCentralDirectoryLocator = 0x07064b50;

	private File _zipFileInfo;

	public class LocalFile implements Closeable, AutoCloseable
	{
		private final EnhancedSeekableByteChannel esbc;

		public String FileName;
		public short _generalPurposeBitFlag;
		private short _compressionMethod;
		private int _lastModFileTime;
		private int _lastModFileDate;
		public byte[] CRC;
		private long _compressedSize;
		public long UncompressedSize;
		public long RelativeOffsetOfLocalHeader; // only in centeral directory

		private long _crc32Location;
		private long _extraLocation;
		private long _dataLocation;

		public boolean Zip64;
		public boolean TrrntZip;

		public byte[] sha1;
		public byte[] md5;

		public ZipReturn FileStatus = ZipReturn.ZipUntested;

		public LocalFile(Path zipfile) throws IOException
		{
			this.esbc = new EnhancedSeekableByteChannel(Files.newByteChannel(zipfile, StandardOpenOption.READ)).order(ByteOrder.LITTLE_ENDIAN);
		}

		public LocalFile(Path zipfile, String filename) throws IOException
		{
			Zip64 = false;
			this.esbc = new EnhancedSeekableByteChannel(Files.newByteChannel(zipfile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ)).order(ByteOrder.LITTLE_ENDIAN);
			_generalPurposeBitFlag = 2; // Maximum Compression Deflating
			_compressionMethod = 8; // Compression Method Deflate
			_lastModFileTime = 48128;
			_lastModFileDate = 8600;

			FileName = filename;
		}

		public ZipReturn CenteralDirectoryRead()
		{
			try
			{

				int thisSignature = esbc.getInt();
				if(thisSignature != CentralDirectoryHeaderSignature)
					return ZipReturn.ZipCenteralDirError;

				esbc.getShort(); // Version Made By

				esbc.getShort(); // Version Needed To Extract

				_generalPurposeBitFlag = esbc.getShort();
				_compressionMethod = esbc.getShort();
				if(_compressionMethod != 8 && _compressionMethod != 0)
					return ZipReturn.ZipUnsupportedCompression;

				_lastModFileTime = esbc.getShort();
				_lastModFileDate = esbc.getShort();
				CRC = ReadCRC(esbc);

				_compressedSize = esbc.getInt();
				UncompressedSize = esbc.getInt();

				int fileNameLength = esbc.getShort();
				int extraFieldLength = esbc.getShort();
				int fileCommentLength = esbc.getShort();

				esbc.getShort(); // diskNumberStart
				esbc.getShort(); // internalFileAttributes
				esbc.getInt(); // externalFileAttributes

				RelativeOffsetOfLocalHeader = esbc.getInt();

				byte[] bFileName = new byte[fileNameLength];
				esbc.get(bFileName);
				FileName = (_generalPurposeBitFlag & (1 << 11)) == 0 ? new String(bFileName, Charset.forName("Cp437")) : new String(bFileName, Charset.forName("UTF8"));

				byte[] extraField = new byte[extraFieldLength];
				esbc.get(extraField);

				esbc.position(esbc.position() + fileCommentLength); // File Comments

				ByteBuffer bb = ByteBuffer.wrap(extraField).order(ByteOrder.LITTLE_ENDIAN);
				while(extraFieldLength > bb.position())
				{
					short type = bb.getShort();
					short blockLength = bb.getShort();
					switch(type)
					{
						case 0x0001:
							Zip64 = true;
							if(UncompressedSize == 0xffffffff)
								UncompressedSize = bb.getLong();
							if(_compressedSize == 0xffffffff)
								_compressedSize = bb.getLong();
							if(RelativeOffsetOfLocalHeader == 0xffffffff)
								RelativeOffsetOfLocalHeader = bb.getLong();
							break;
						case 0x7075:
							@SuppressWarnings("unused") byte version = bb.get();
							long nameCRC32 = bb.getInt();

							java.util.zip.CRC32 crcTest = new java.util.zip.CRC32();
							crcTest.update(bFileName);
							long fCRC = crcTest.getValue();

							if(nameCRC32 != fCRC)
								return ZipReturn.ZipCenteralDirError;

							int charLen = blockLength - 5;

							byte[] dst = new byte[charLen];
							bb.get(dst);
							FileName = new String(dst, Charset.forName("UTF8"));

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
				return ZipReturn.ZipCenteralDirError;
			}

		}

		public void CenteralDirectoryWrite(EnhancedSeekableByteChannel esbc) throws IOException
		{

			final int header = 0x02014B50;

			List<Byte> extraField = new ArrayList<>();

			long cdUncompressedSize;
			if(UncompressedSize >= 0xffffffffL)
			{
				Zip64 = true;
				cdUncompressedSize = 0xffffffff;
				for(byte b : ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putLong(UncompressedSize).array())
					extraField.add(b);
			}
			else
				cdUncompressedSize = UncompressedSize;

			long cdCompressedSize;
			if(_compressedSize >= 0xffffffff)
			{
				Zip64 = true;
				cdCompressedSize = 0xffffffff;
				for(byte b : ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putLong(_compressedSize).array())
					extraField.add(b);
			}
			else
				cdCompressedSize = _compressedSize;

			long cdRelativeOffsetOfLocalHeader;
			if(RelativeOffsetOfLocalHeader >= 0xffffffff)
			{
				Zip64 = true;
				cdRelativeOffsetOfLocalHeader = 0xffffffff;
				for(byte b : ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putLong(RelativeOffsetOfLocalHeader).array())
					extraField.add(b);
			}
			else
				cdRelativeOffsetOfLocalHeader = RelativeOffsetOfLocalHeader;

			if(extraField.size() > 0)
			{
				int exfl = extraField.size();
				int i = 0;
				for(byte b : ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putShort((short) 0x0001).array())
					extraField.add(i++, b);
				for(byte b : ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putShort((short) exfl).array())
					extraField.add(i++, b);
			}
			int extraFieldLength = extraField.size();

			byte[] bFileName;

			if(!Charset.forName("Cp437").newEncoder().canEncode(FileName))
			{
				_generalPurposeBitFlag |= 1 << 11;
				bFileName = FileName.getBytes(Charset.forName("UTF8"));
			}
			else
				bFileName = FileName.getBytes(Charset.forName("Cp437"));
			int fileNameLength = bFileName.length;

			int versionNeededToExtract = (Zip64 ? 45 : 20);

			esbc.putInt(header); // 4
			esbc.putShort((short) 0); // 6
			esbc.putShort((short) versionNeededToExtract); // 8
			esbc.putShort((short) _generalPurposeBitFlag); // 10
			esbc.putShort((short) _compressionMethod); // 12
			esbc.putShort((short) _lastModFileTime); // 14
			esbc.putShort((short) _lastModFileDate); // 16
			esbc.put(CRC); // 20
			esbc.putInt((int) cdCompressedSize); // 24
			esbc.putInt((int) cdUncompressedSize); // 28
			esbc.putShort((short) fileNameLength); // 30
			esbc.putShort((short) extraFieldLength); // 32
			esbc.putShort((short) 0); // 34 // file comment length
			esbc.putShort((short) 0); // 36 // disk number start
			esbc.putShort((short) 0); // 38 // internal file attributes
			esbc.putInt((int) 0); // 42 // external file attributes
			esbc.putInt((int) cdRelativeOffsetOfLocalHeader); // 46
			esbc.put(bFileName);
			for(byte b : extraField)
				esbc.put(b);
			// No File Comment
		}

		public ZipReturn LocalFileHeaderRead()
		{
			try
			{
				TrrntZip = true;

				esbc.position((int) RelativeOffsetOfLocalHeader);
				int thisSignature = esbc.getInt();
				if(thisSignature != LocalFileHeaderSignature)
					return ZipReturn.ZipLocalFileHeaderError;

				esbc.getShort(); // version needed to extract
				int generalPurposeBitFlagLocal = (int) esbc.getShort();
				if(generalPurposeBitFlagLocal != _generalPurposeBitFlag)
					TrrntZip = false;

				int tshort = (int) esbc.getShort();
				if(tshort != _compressionMethod)
					return ZipReturn.ZipLocalFileHeaderError;

				tshort = (int) esbc.getShort();
				if(tshort != _lastModFileTime)
					return ZipReturn.ZipLocalFileHeaderError;

				tshort = (int) esbc.getShort();
				if(tshort != _lastModFileDate)
					return ZipReturn.ZipLocalFileHeaderError;

				byte[] tCRC = ReadCRC(esbc);
				if(((_generalPurposeBitFlag & 8) == 0) && !Arrays.equals(tCRC, CRC))
					return ZipReturn.ZipLocalFileHeaderError;

				long tCompressedSize = (long) esbc.getInt();
				if(Zip64 && tCompressedSize != 0xffffffff && tCompressedSize != _compressedSize) // if Zip64 File then the compressedSize should be 0xffffffff
					return ZipReturn.ZipLocalFileHeaderError;
				if((_generalPurposeBitFlag & 8) == 8 && tCompressedSize != 0) // if bit 4 set then no compressedSize is set yet
					return ZipReturn.ZipLocalFileHeaderError;
				if(!Zip64 && (_generalPurposeBitFlag & 8) != 8 && tCompressedSize != _compressedSize) // check the compressedSize
					return ZipReturn.ZipLocalFileHeaderError;

				long tUnCompressedSize = (long) esbc.getInt();
				if(Zip64 && tUnCompressedSize != 0xffffffff && tUnCompressedSize != UncompressedSize) // if Zip64 File then the unCompressedSize should be 0xffffffff
					return ZipReturn.ZipLocalFileHeaderError;
				if((_generalPurposeBitFlag & 8) == 8 && tUnCompressedSize != 0) // if bit 4 set then no unCompressedSize is set yet
					return ZipReturn.ZipLocalFileHeaderError;
				if(!Zip64 && (_generalPurposeBitFlag & 8) != 8 && tUnCompressedSize != UncompressedSize) // check the unCompressedSize
					return ZipReturn.ZipLocalFileHeaderError;

				int fileNameLength = (int) esbc.getShort();
				int extraFieldLength = (int) esbc.getShort();

				byte[] bFileName = new byte[fileNameLength];
				esbc.get(bFileName);
				String tFileName = (generalPurposeBitFlagLocal & (1 << 11)) == 0 ? new String(bFileName, Charset.forName("Cp437")) : new String(bFileName, Charset.forName("UTF8"));

				byte[] extraField = new byte[extraFieldLength];
				esbc.get(extraField);

				Zip64 = false;
				ByteBuffer bb = ByteBuffer.wrap(extraField).order(ByteOrder.LITTLE_ENDIAN);
				while(extraFieldLength > bb.position())
				{
					int type = (int) bb.getShort();
					int blockLength = (int) bb.getShort();
					switch(type)
					{
						case 0x0001:
							Zip64 = true;
							if(tUnCompressedSize == 0xffffffff)
							{
								long tLong = bb.getLong();
								if(tLong != UncompressedSize)
									return ZipReturn.ZipLocalFileHeaderError;
							}
							if(tCompressedSize == 0xffffffff)
							{
								long tLong = bb.getLong();
								if(tLong != _compressedSize)
									return ZipReturn.ZipLocalFileHeaderError;
							}
							break;
						case 0x7075:
							@SuppressWarnings("unused") byte version = bb.get();
							long nameCRC32 = (long) bb.getInt();

							java.util.zip.CRC32 crcTest = new java.util.zip.CRC32();
							crcTest.update(bFileName);
							long fCRC = crcTest.getValue();

							if(nameCRC32 != fCRC)
								return ZipReturn.ZipLocalFileHeaderError;

							int charLen = blockLength - 5;

							byte[] dst = new byte[charLen];
							bb.get(dst);
							FileName = new String(dst, Charset.forName("UTF8"));

							break;
						default:
							bb.position(bb.position() + blockLength);
							break;
					}
				}

				if(!FileName.equals(tFileName))
					return ZipReturn.ZipLocalFileHeaderError;

				_dataLocation = esbc.position();

				if((_generalPurposeBitFlag & 8) == 0)
					return ZipReturn.ZipGood;

				esbc.position(esbc.position() + (int) _compressedSize);

				tCRC = ReadCRC(esbc);
				if(!Arrays.equals(tCRC, new byte[] { 0x50, 0x4b, 0x07, 0x08 }))
					tCRC = ReadCRC(esbc);

				if(!Arrays.equals(tCRC, CRC))
					return ZipReturn.ZipLocalFileHeaderError;

				long tint = (long) esbc.getInt();
				if(tint != _compressedSize)
					return ZipReturn.ZipLocalFileHeaderError;

				tint = (long) esbc.getInt();
				if(tint != UncompressedSize)
					return ZipReturn.ZipLocalFileHeaderError;

				return ZipReturn.ZipGood;
			}
			catch(Exception e)
			{
				e.printStackTrace();
				return ZipReturn.ZipLocalFileHeaderError;
			}

		}

		public ZipReturn LocalFileHeaderReadQuick()
		{
			try
			{
				TrrntZip = true;

				esbc.position((int) RelativeOffsetOfLocalHeader);
				int thisSignature = esbc.getInt();
				if(thisSignature != LocalFileHeaderSignature)
					return ZipReturn.ZipLocalFileHeaderError;

				esbc.getShort(); // version needed to extract
				_generalPurposeBitFlag = esbc.getShort();
				if((_generalPurposeBitFlag & 8) == 8)
					return ZipReturn.ZipCannotFastOpen;

				_compressionMethod = esbc.getShort();
				_lastModFileTime = (int) esbc.getShort();
				_lastModFileDate = (int) esbc.getShort();
				CRC = ReadCRC(esbc);
				_compressedSize = (long) esbc.getInt();
				UncompressedSize = (long) esbc.getInt();

				int fileNameLength = (int) esbc.getShort();
				int extraFieldLength = (int) esbc.getShort();

				byte[] bFileName = new byte[fileNameLength];
				esbc.get(bFileName);
				String tFileName = (_generalPurposeBitFlag & (1 << 11)) == 0 ? new String(bFileName, Charset.forName("Cp437")) : new String(bFileName, Charset.forName("UTF8"));

				byte[] extraField = new byte[extraFieldLength];
				esbc.get(extraField);

				Zip64 = false;
				ByteBuffer bb = ByteBuffer.wrap(extraField).order(ByteOrder.LITTLE_ENDIAN);
				while(extraFieldLength > bb.position())
				{
					int type = (int) bb.getShort();
					int blockLength = (int) bb.getShort();
					switch(type)
					{
						case 0x0001:
							Zip64 = true;
							if(UncompressedSize == 0xffffffff)
								UncompressedSize = bb.getLong();
							if(_compressedSize == 0xffffffff)
								_compressedSize = bb.getLong();
							break;
						case 0x7075:
							@SuppressWarnings("unused") byte version = bb.get();
							long nameCRC32 = (long) bb.getInt();

							java.util.zip.CRC32 crcTest = new java.util.zip.CRC32();
							crcTest.update(bFileName);
							long fCRC = crcTest.getValue();

							if(nameCRC32 != fCRC)
								return ZipReturn.ZipLocalFileHeaderError;

							int charLen = blockLength - 5;

							byte[] dst = new byte[charLen];
							bb.get(dst);
							FileName = new String(dst, Charset.forName("UTF8"));
							break;
						default:
							bb.position(bb.position() + blockLength);
							break;
					}
				}

				_dataLocation = (long) esbc.position();
				return ZipReturn.ZipGood;

			}
			catch(Exception e)
			{
				e.printStackTrace();
				return ZipReturn.ZipLocalFileHeaderError;
			}

		}

		private void LocalFileHeaderWrite() throws IOException
		{
			List<Byte> extraField = new ArrayList<>();
			Zip64 = UncompressedSize >= 0xffffffff;

			byte[] bFileName;

			if(!Charset.forName("Cp437").newEncoder().canEncode(FileName))
			{
				_generalPurposeBitFlag |= 1 << 11;
				bFileName = FileName.getBytes(Charset.forName("UTF8"));
			}
			else
				bFileName = FileName.getBytes(Charset.forName("Cp437"));

			int versionNeededToExtract = (Zip64 ? 45 : 20);

			RelativeOffsetOfLocalHeader = esbc.position();
			final int header = 0x4034B50;

			esbc.putInt(header); // 4
			esbc.putShort((short) versionNeededToExtract); // 8
			esbc.putShort((short) _generalPurposeBitFlag); // 10
			esbc.putShort((short) _compressionMethod); // 12
			esbc.putShort((short) _lastModFileTime); // 14
			esbc.putShort((short) _lastModFileDate); // 16

			_crc32Location = esbc.position();

			// these 3 values will be set correctly after the file data has been written
			esbc.putInt(0xFFFFFFFF);
			esbc.putInt(0xFFFFFFFF);
			esbc.putInt(0xFFFFFFFF);

			if(Zip64)
			{
				for(int i = 0; i < 20; i++)
					extraField.add((byte) 0);
			}

			int fileNameLength = bFileName.length;
			esbc.putShort((short) fileNameLength);

			int extraFieldLength = extraField.size();
			esbc.putShort((short) extraFieldLength);

			esbc.put(bFileName);

			_extraLocation = esbc.position();
			for(byte b : extraField)
				esbc.put(b);
		}

		private InputStream _readStream;

		public ZipReturn LocalFileOpenReadStream(boolean raw, AtomicReference<InputStream> stream, AtomicLong streamSize, AtomicInteger compressionMethod) throws IOException
		{
			streamSize.set(0);
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
						streamSize.set(UncompressedSize);

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

		public ZipReturn LocalFileCloseReadStream() throws IOException
		{
			InputStream dfStream = _readStream;
			if(dfStream != null)
			{
				dfStream.close();
			}
			close();
			return ZipReturn.ZipGood;
		}

		private OutputStream _writeStream;

		public ZipReturn LocalFileOpenWriteStream(boolean raw, boolean trrntZip, long uncompressedSize, short compressionMethod, AtomicReference<OutputStream> stream) throws IOException
		{
			UncompressedSize = uncompressedSize;
			_compressionMethod = compressionMethod;

			LocalFileHeaderWrite();
			_dataLocation = esbc.position();

			if(raw)
			{
				_writeStream = esbc.getOutputStream();
				TrrntZip = trrntZip;
			}
			else
			{
				if(compressionMethod == 0)
				{
					_writeStream = esbc.getOutputStream();
					TrrntZip = false;
				}
				else
				{
					_writeStream = new DeflaterOutputStream(esbc.getOutputStream(), new Deflater(9, true));
					TrrntZip = true;
				}
			}

			stream.set(_writeStream);
			return stream.get() == null ? ZipReturn.ZipErrorGettingDataStream : ZipReturn.ZipGood;
		}

		public ZipReturn LocalFileCloseWriteStream(byte[] crc32) throws IOException
		{
			OutputStream dfStream = _writeStream;
			if(dfStream != null)
			{
				dfStream.flush();
				// dfStream.close();
			}

			_compressedSize = esbc.position() - _dataLocation;

			if(_compressedSize == 0 && UncompressedSize == 0)
			{
				LocalFileAddDirectory();
				_compressedSize = esbc.position() - _dataLocation;
			}

			CRC = crc32;
			WriteCompressedSize();
			close();
			return ZipReturn.ZipGood;
		}

		private void WriteCompressedSize() throws IOException
		{
			long posNow = esbc.position();

			esbc.position(_crc32Location);

			long tCompressedSize;
			long tUncompressedSize;
			if(Zip64)
			{
				tCompressedSize = 0xffffffff;
				tUncompressedSize = 0xffffffff;
			}
			else
			{
				tCompressedSize = _compressedSize;
				tUncompressedSize = UncompressedSize;
			}

			esbc.put(CRC);
			esbc.putInt((int) tCompressedSize);
			esbc.putInt((int) tUncompressedSize);

			// also need to write extradata
			if(Zip64)
			{
				esbc.position(_extraLocation);
				esbc.putShort((short) 0x0001); // id
				esbc.putShort((short) 16); // data length
				esbc.putLong(UncompressedSize);
				esbc.putLong(_compressedSize);
			}

			esbc.position(posNow);

		}

		public void LocalFileCheck()
		{
			if(FileStatus != ZipReturn.ZipUntested)
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
					FileStatus = ZipReturn.ZipErrorGettingDataStream;
					return;
				}

				if(_buffer0 == null)
				{
					_buffer0 = new byte[Buffersize];
				}

				long sizetogo = UncompressedSize;

				java.util.zip.CRC32 tcrc32 = new java.util.zip.CRC32();
				MessageDigest tmd5 = MessageDigest.getInstance("MD5");
				MessageDigest tsha1 = MessageDigest.getInstance("SHA-1");

				// Pre load the first buffer0
				int sizeNext = sizetogo > Buffersize ? Buffersize : (int) sizetogo;
				int sizebuffer = sInput.read(_buffer0, 0, sizeNext);
				sizetogo -= sizebuffer;

				while(sizebuffer > 0)
				{
					sizeNext = sizetogo > Buffersize ? Buffersize : (int) sizetogo;

					if(sizeNext > 0)
					{
						sizebuffer = sInput.read(_buffer0, 0, sizeNext);

						if(sizebuffer > 0)
						{
							tcrc32.update(_buffer0, 0, sizebuffer);
							tmd5.update(_buffer0, 0, sizebuffer);
							tsha1.update(_buffer0, 0, sizebuffer);
							sizetogo -= sizebuffer;
						}
					}
				}

				if(sizetogo > 0)
				{
					FileStatus = ZipReturn.ZipDecodeError;
					return;
				}

				byte[] testcrc = ByteBuffer.allocate(4).putInt((int) tcrc32.getValue()).array();
				md5 = tmd5.digest();
				sha1 = tsha1.digest();

				FileStatus = Arrays.equals(CRC, testcrc) ? ZipReturn.ZipGood : ZipReturn.ZipCRCDecodeError;
			}
			catch(Exception e)
			{
				FileStatus = ZipReturn.ZipDecodeError;
			}
		}

		public void LocalFileAddDirectory() throws IOException
		{
			esbc.put((byte) 3);
			esbc.put((byte) 0);
		}

		public long LocalFilePos()
		{
			return RelativeOffsetOfLocalHeader;
		}

		public void LocalFilePos(long value)
		{
			RelativeOffsetOfLocalHeader = value;
		}

		private byte[] ReadCRC(EnhancedSeekableByteChannel esbc) throws IOException
		{
			return ByteBuffer.allocate(4).putInt(esbc.getInt()).array();
		}

		@Override
		public void close() throws IOException
		{
			if(this.esbc != null)
				this.esbc.close();
		}

	}

	public String ZipFilename()
	{
		return _zipFileInfo != null ? _zipFileInfo.getAbsolutePath() : "";
	}

	public long TimeStamp()
	{
		return _zipFileInfo != null ? _zipFileInfo.lastModified() : 0;
	}

	private long _centerDirStart;
	private long _centerDirSize;
	private long _endOfCenterDir64;

	byte[] _fileComment;
	private EnhancedSeekableByteChannel _esbc;

	private int _localFilesCount;
	private final List<LocalFile> _localFiles = new ArrayList<>();

	private EnumSet<ZipStatus> _pZipStatus = EnumSet.noneOf(ZipStatus.class);
	private boolean _zip64;
	private ZipOpenType ZipOpen;

	public ZipOpenType ZipOpen()
	{
		return ZipOpen;
	}

	public EnumSet<ZipStatus> ZipStatus()
	{
		return _pZipStatus;
	}

	public int LocalFilesCount()
	{
		return _localFiles.size();
	}

	public String Filename(int i)
	{
		return _localFiles.get(i).FileName;
	}

	public long UncompressedSize(int i)
	{
		return _localFiles.get(i).UncompressedSize;
	}

	public Long LocalHeader(int i)
	{
		return ((_localFiles.get(i)._generalPurposeBitFlag & 8) == 0) ? (Long) _localFiles.get(i).RelativeOffsetOfLocalHeader : null;
	}

	public ZipReturn FileStatus(int i)
	{
		return _localFiles.get(i).FileStatus;
	}

	public byte[] CRC32(int i)
	{
		return _localFiles.get(i).CRC;
	}

	public byte[] MD5(int i)
	{
		return _localFiles.get(i).md5;
	}

	public byte[] SHA1(int i)
	{
		return _localFiles.get(i).sha1;
	}

	@Override
	public void close()
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

	private ZipReturn FindEndOfCentralDirSignature() throws IOException
	{
		long fileSize = _esbc.size();
		long maxBackSearch = 0xffff;

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
		return ZipReturn.ZipCenteralDirError;
	}

	private ZipReturn EndOfCentralDirRead() throws IOException
	{
		long thisSignature = _esbc.getInt();
		if(thisSignature != EndOfCentralDirSignature)
			return ZipReturn.ZipEndOfCentralDirectoryError;

		int tushort = _esbc.getShort(); // NumberOfThisDisk
		if(tushort != 0)
			return ZipReturn.ZipEndOfCentralDirectoryError;

		tushort = _esbc.getShort(); // NumberOfThisDiskCenterDir
		if(tushort != 0)
			return ZipReturn.ZipEndOfCentralDirectoryError;

		_localFilesCount = _esbc.getShort(); // TotalNumberOfEnteriesDisk

		tushort = _esbc.getShort(); // TotalNumber of enteries in the central directory
		if(tushort != _localFilesCount)
			return ZipReturn.ZipEndOfCentralDirectoryError;

		_centerDirSize = _esbc.getInt(); // SizeOfCenteralDir
		_centerDirStart = _esbc.getInt(); // Offset

		int zipFileCommentLength = _esbc.getShort();

		_fileComment = new byte[zipFileCommentLength];
		_esbc.get(_fileComment);

		if(_esbc.position() != _esbc.size())
			_pZipStatus.add(ZipStatus.ExtraData);

		return ZipReturn.ZipGood;
	}

	private void EndOfCentralDirWrite() throws IOException
	{
		_esbc.putInt(EndOfCentralDirSignature);
		_esbc.putShort((short) 0); // NumberOfThisDisk
		_esbc.putShort((short) 0); // NumberOfThisDiskCenterDir
		_esbc.putShort((short) (_localFiles.size() >= 0xffff ? 0xffff : _localFiles.size())); // TotalNumberOfEnteriesDisk
		_esbc.putShort((short) (_localFiles.size() >= 0xffff ? 0xffff : _localFiles.size())); // TotalNumber of enteries in the central directory
		_esbc.putInt((int) (_centerDirSize >= 0xffffffff ? 0xffffffff : _centerDirSize));
		_esbc.putInt((int) (_centerDirStart >= 0xffffffff ? 0xffffffff : _centerDirStart));
		_esbc.putShort((short) _fileComment.length);
		_esbc.put(_fileComment, 0, _fileComment.length);
	}

	private ZipReturn Zip64EndOfCentralDirRead() throws IOException
	{
		_zip64 = true;

		long thisSignature = _esbc.getInt();
		if(thisSignature != Zip64EndOfCentralDirSignature)
			return ZipReturn.ZipEndOfCentralDirectoryError;

		long tulong = _esbc.getLong(); // Size of zip64 end of central directory record
		if(tulong != 44)
			return ZipReturn.Zip64EndOfCentralDirError;

		_esbc.getShort(); // version made by

		int tushort = _esbc.getShort(); // version needed to extract
		if(tushort != 45)
			return ZipReturn.Zip64EndOfCentralDirError;

		long tuint = _esbc.getInt(); // number of this disk
		if(tuint != 0)
			return ZipReturn.Zip64EndOfCentralDirError;

		tuint = _esbc.getInt(); // number of the disk with the start of the central directory
		if(tuint != 0)
			return ZipReturn.Zip64EndOfCentralDirError;

		_localFilesCount = (int) _esbc.getLong(); // total number of entries in the central directory on this disk

		tulong = _esbc.getLong(); // total number of entries in the central directory
		if(tulong != _localFilesCount)
			return ZipReturn.Zip64EndOfCentralDirError;

		_centerDirSize = _esbc.getLong(); // size of central directory

		_centerDirStart = _esbc.getLong(); // offset of start of central directory with respect to the starting disk number

		return ZipReturn.ZipGood;
	}

	private void Zip64EndOfCentralDirWrite() throws IOException
	{
		_esbc.putInt(Zip64EndOfCentralDirSignature);
		_esbc.putLong(44L); // Size of zip64 end of central directory record
		_esbc.putShort((short) 45); // version made by
		_esbc.putShort((short) 45); // version needed to extract
		_esbc.putInt((int) 0); // number of this disk
		_esbc.putInt((int) 0); // number of the disk with the start of the central directroy
		_esbc.putLong((long) _localFiles.size()); // total number of entries in the central directory on this disk
		_esbc.putLong((long) _localFiles.size()); // total number of entries in the central directory
		_esbc.putLong(_centerDirSize); // size of central directory
		_esbc.putLong(_centerDirStart); // offset of start of central directory with respect to the starting disk number
	}

	private ZipReturn Zip64EndOfCentralDirectoryLocatorRead() throws IOException
	{
		_zip64 = true;

		long thisSignature = _esbc.getInt();
		if(thisSignature != Zip64EndOfCentralDirectoryLocator)
			return ZipReturn.ZipEndOfCentralDirectoryError;

		long tuint = _esbc.getInt(); // number of the disk with the start of the zip64 end of centeral directory
		if(tuint != 0)
			return ZipReturn.Zip64EndOfCentralDirectoryLocatorError;

		_endOfCenterDir64 = _esbc.getLong(); // relative offset of the zip64 end of central directroy record

		tuint = _esbc.getInt(); // total number of disks
		if(tuint != 1)
			return ZipReturn.Zip64EndOfCentralDirectoryLocatorError;

		return ZipReturn.ZipGood;
	}

	private void Zip64EndOfCentralDirectoryLocatorWrite() throws IOException
	{
		_esbc.putInt(Zip64EndOfCentralDirectoryLocator);
		_esbc.putInt(0); // number of the disk with the start of the zip64 end of centeral directory
		_esbc.putLong(_endOfCenterDir64); // relative offset of the zip64 end of central directroy record
		_esbc.putInt(1); // total number of disks
	}

	public ZipReturn ZipFileOpen(File newFilename, long timestamp, boolean readHeaders) throws IOException
	{
		ZipFileClose();
		_pZipStatus = EnumSet.noneOf(ZipStatus.class);
		_zip64 = false;
		_centerDirStart = 0;
		_centerDirSize = 0;
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
			_esbc = new EnhancedSeekableByteChannel(Files.newByteChannel(newFilename.toPath(), StandardOpenOption.READ));
		}
		catch(IOException e)
		{
			ZipFileClose();
			return ZipReturn.ZipErrorOpeningFile;
		}
		ZipOpen = ZipOpenType.OpenRead;

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
			if(_centerDirStart == 0xffffffff || _centerDirSize == 0xffffffff || _localFilesCount == 0xffff)
			{
				_zip64 = true;
				_esbc.position(endOfCentralDir - 20);
				zRet = Zip64EndOfCentralDirectoryLocatorRead();
				if(zRet != ZipReturn.ZipGood)
				{
					ZipFileClose();
					return zRet;
				}
				_esbc.position(_endOfCenterDir64);
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
				if(new String(_fileComment, Charset.forName("Cp437")).substring(0, 14) == "TORRENTZIPPED-")
				{
					EnhancedSeekableByteChannel crcCs = new EnhancedSeekableByteChannel(Files.newByteChannel(newFilename.toPath(), StandardOpenOption.READ));
					byte[] buffer = new byte[(int) _centerDirSize];
					crcCs.position(_centerDirStart);
					crcCs.get(buffer);
					crcCs.close();

					long r = crcCs.getChecksum();

					String tcrc = new String(_fileComment, Charset.forName("Cp437")).substring(14, 22);
					String zcrc = Integer.toHexString((int) r);
					if(tcrc.equalsIgnoreCase(zcrc))
						trrntzip = true;

				}
			}

			// now read the central directory
			_esbc.position(_centerDirStart);

			_localFiles.clear();
			for(int i = 0; i < _localFilesCount; i++)
			{
				LocalFile lc = new LocalFile(newFilename.toPath());
				zRet = lc.CenteralDirectoryRead();
				if(zRet != ZipReturn.ZipGood)
				{
					ZipFileClose();
					lc.close();
					return zRet;
				}
				_zip64 |= lc.Zip64;
				_localFiles.add(lc);
			}

			for(int i = 0; i < _localFilesCount; i++)
			{
				zRet = _localFiles.get(i).LocalFileHeaderRead();
				if(zRet != ZipReturn.ZipGood)
				{
					ZipFileClose();
					return zRet;
				}
				trrntzip &= _localFiles.get(i).TrrntZip;
			}

			// check trrntzip file order
			if(trrntzip)
				for(int i = 0; i < _localFilesCount - 1; i++)
				{
					if(_localFiles.get(i).FileName.compareToIgnoreCase(_localFiles.get(i + 1).FileName) < 0)
						continue;
					trrntzip = false;
					break;
				}

			// check trrntzip directories
			if(trrntzip)
				for(int i = 0; i < _localFilesCount - 1; i++)
				{
					// see if we found a directory
					String filename0 = _localFiles.get(i).FileName;
					if(!filename0.endsWith("/"))
						continue;

					// see if the next file is in that directory
					String filename1 = _localFiles.get(i + 1).FileName;
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

	public ZipReturn ZipFileCreate(File newFilename) throws IOException
	{
		if(ZipOpen != ZipOpenType.Closed)
			return ZipReturn.ZipFileAlreadyOpen;

		CreateDirForFile(newFilename);
		_zipFileInfo = newFilename;

		_esbc = new EnhancedSeekableByteChannel(Files.newByteChannel(newFilename.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ));
		ZipOpen = ZipOpenType.OpenWrite;
		return ZipReturn.ZipGood;
	}

	public void ZipFileClose() throws IOException
	{
		if(ZipOpen == ZipOpenType.Closed)
		{
			return;
		}

		if(ZipOpen == ZipOpenType.OpenRead)
		{
			if(_esbc != null)
			{
				_esbc.close();
			}
			ZipOpen = ZipOpenType.Closed;
			return;
		}

		_zip64 = false;
		boolean lTrrntzip = true;

		_centerDirStart = _esbc.position();
		if(_centerDirStart >= 0xffffffff)
			_zip64 = true;

		EnhancedSeekableByteChannel crcCs = new EnhancedSeekableByteChannel(Files.newByteChannel(_zipFileInfo.toPath(), StandardOpenOption.READ));
		for(LocalFile t : _localFiles)
		{
			t.CenteralDirectoryWrite(crcCs);
			_zip64 |= t.Zip64;
			lTrrntzip &= t.TrrntZip;
		}
		crcCs.close();

		_centerDirSize = _esbc.position() - _centerDirStart;

		_fileComment = lTrrntzip ? ("TORRENTZIPPED-" + Integer.toHexString((int) crcCs.getChecksum())).getBytes(Charset.forName("Cp437")) : new byte[0];
		_pZipStatus = lTrrntzip ? EnumSet.of(ZipStatus.TrrntZip) : EnumSet.noneOf(ZipStatus.class);

		if(_zip64)
		{
			_endOfCenterDir64 = _esbc.position();
			Zip64EndOfCentralDirWrite();
			Zip64EndOfCentralDirectoryLocatorWrite();
		}
		EndOfCentralDirWrite();

		_esbc.truncate(_esbc.position());
		close();
		ZipOpen = ZipOpenType.Closed;

	}

	public void ZipFileCloseFailed() throws IOException
	{
		if(ZipOpen == ZipOpenType.Closed)
		{
			return;
		}

		if(ZipOpen == ZipOpenType.OpenRead)
		{
			if(_esbc != null)
			{
				_esbc.close();
			}
			ZipOpen = ZipOpenType.Closed;
			return;
		}

		_esbc.close();
		_zipFileInfo.delete();
		_zipFileInfo = null;
		ZipOpen = ZipOpenType.Closed;
	}

	private int _readIndex;

	public ZipReturn ZipFileOpenReadStream(int index, boolean raw, AtomicReference<InputStream> stream, AtomicLong streamSize, AtomicInteger compressionMethod) throws IOException
	{
		streamSize.set(0);
		compressionMethod.set(0);
		_readIndex = index;
		stream = null;
		if(ZipOpen != ZipOpenType.OpenRead)
			return ZipReturn.ZipReadingFromOutputFile;

		ZipReturn zRet = _localFiles.get(index).LocalFileHeaderRead();
		if(zRet != ZipReturn.ZipGood)
		{
			ZipFileClose();
			return zRet;
		}

		return _localFiles.get(index).LocalFileOpenReadStream(raw, stream, streamSize, compressionMethod);
	}

	public ZipReturn ZipFileOpenReadStreamQuick(long pos, boolean raw, AtomicReference<InputStream> stream, AtomicLong streamSize, AtomicInteger compressionMethod) throws IOException
	{
		LocalFile tmpFile = new LocalFile(_zipFileInfo.toPath())
		{
			{
				LocalFilePos(pos);
			}
		};
		_localFiles.clear();
		_localFiles.add(tmpFile);
		ZipReturn zr = tmpFile.LocalFileHeaderReadQuick();
		if(zr != ZipReturn.ZipGood)
		{
			stream.set(null);
			streamSize.set(0);
			compressionMethod.set(0);
			return zr;
		}
		_readIndex = 0;

		return tmpFile.LocalFileOpenReadStream(raw, stream, streamSize, compressionMethod);
	}

	public ZipReturn ZipFileCloseReadStream() throws IOException
	{
		return _localFiles.get(_readIndex).LocalFileCloseReadStream();
	}

	public ZipReturn ZipFileOpenWriteStream(boolean raw, boolean trrntzip, String filename, long uncompressedSize, short compressionMethod, AtomicReference<OutputStream> stream) throws IOException
	{
		stream = null;
		if(ZipOpen != ZipOpenType.OpenWrite)
			return ZipReturn.ZipWritingToInputFile;

		LocalFile lf = new LocalFile(_zipFileInfo.toPath(), filename);

		ZipReturn retVal = lf.LocalFileOpenWriteStream(raw, trrntzip, uncompressedSize, compressionMethod, stream);

		_localFiles.add(lf);

		return retVal;
	}

	public ZipReturn ZipFileCloseWriteStream(byte[] crc32) throws IOException
	{
		return _localFiles.get(_localFiles.size() - 1).LocalFileCloseWriteStream(crc32);
	}

	public ZipReturn ZipFileRollBack() throws IOException
	{
		if(ZipOpen != ZipOpenType.OpenWrite)
			return ZipReturn.ZipWritingToInputFile;

		int fileCount = _localFiles.size();
		if(fileCount == 0)
			return ZipReturn.ZipErrorRollBackFile;

		LocalFile lf = _localFiles.get(fileCount - 1);

		_localFiles.remove(fileCount - 1);
		_esbc.position(lf.LocalFilePos());
		return ZipReturn.ZipGood;
	}

	public void ZipFileAddDirectory() throws IOException
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

	public void DeepScan()
	{
		for(LocalFile lfile : _localFiles)
			lfile.LocalFileCheck();
	}

	private static void CreateDirForFile(File sFilename)
	{
		sFilename.getParentFile().mkdirs();
	}

	public static String ZipErrorMessageText(ZipReturn zS)
	{
		String ret = "Unknown";
		switch(zS)
		{
			case ZipGood:
				ret = "";
				break;
			case ZipFileCountError:
				ret = "The number of file in the Zip does not mach the number of files in the Zips Centeral Directory";
				break;
			case ZipSignatureError:
				ret = "An unknown Signature Block was found in the Zip";
				break;
			case ZipExtraDataOnEndOfZip:
				ret = "Extra Data was found on the end of the Zip";
				break;
			case ZipUnsupportedCompression:
				ret = "An unsupported Compression method was found in the Zip, if you recompress this zip it will be usable";
				break;
			case ZipLocalFileHeaderError:
				ret = "Error reading a zipped file header information";
				break;
			case ZipCenteralDirError:
				ret = "There is an error in the Zip Centeral Directory";
				break;
			case ZipReadingFromOutputFile:
				ret = "Trying to write to a Zip file open for output only";
				break;
			case ZipWritingToInputFile:
				ret = "Tring to read from a Zip file open for input only";
				break;
			case ZipErrorGettingDataStream:
				ret = "Error creating Data Stream";
				break;
			case ZipCRCDecodeError:
				ret = "CRC error";
				break;
			case ZipDecodeError:
				ret = "Error unzipping a file";
				break;
			default:
				ret = zS.toString();
				break;
		}

		return ret;
	}

}
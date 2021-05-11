package JTrrntzip;

import java.util.EnumSet;
import java.util.List;

public class TorrentZipCheck
{
	private TorrentZipCheck()
	{
		throw new IllegalStateException("Utility class");
	}

	public static EnumSet<TrrntZipStatus> CheckZipFiles(final List<ZippedFile> zippedFiles, final LogCallback StatusLogCallBack)
	{
		final EnumSet<TrrntZipStatus> tzStatus = EnumSet.noneOf(TrrntZipStatus.class);

		// ***************************** RULE 1 *************************************
		// Directory separator should be a '/' a '\' is invalid and should be replaced with '/'
		//
		// check if any '\' = 92 need converted to '/' = 47
		// this needs done before the sort, so that the sort is correct.
		// return BadDirectorySeparator if errors found.
		var error1 = false;
		for(final ZippedFile t : zippedFiles)
		{
			final char[] bytes = t.Name.toCharArray();
			var fixDir = false;
			for(var j = 0; j < bytes.length; j++)
			{
				if(bytes[j] != '\\')
					continue;
				fixDir = true;
				bytes[j] = '/';
				tzStatus.add(TrrntZipStatus.BadDirectorySeparator);
				if(!error1 && StatusLogCallBack.isVerboseLogging())
				{
					error1 = true;
					StatusLogCallBack.StatusLogCallBack(Messages.getString("TorrentZipCheck.IncorrectDirectorySeparatoreFound")); //$NON-NLS-1$
				}
			}
			if(fixDir)
				t.Name = new String(bytes);
		}

		// ***************************** RULE 2 *************************************
		// All Files in a torrentzip should be sorted with a lower case file compare.
		//
		// if needed sort the files correctly, and return Unsorted if errors found.
		var error2 = false;
		var thisSortFound = true;
		while(thisSortFound)
		{
			thisSortFound = false;
			for(var i = 0; i < zippedFiles.size() - 1; i++)
			{
				final int c = zippedFiles.get(i).Name.compareToIgnoreCase(zippedFiles.get(i + 1).Name);
				if(c > 0)
				{
					final var T = zippedFiles.get(i);
					zippedFiles.set(i, zippedFiles.get(i + 1));
					zippedFiles.set(i + 1, T);

					tzStatus.add(TrrntZipStatus.Unsorted);
					thisSortFound = true;
					if(!error2 && StatusLogCallBack.isVerboseLogging())
					{
						error2 = true;
						StatusLogCallBack.StatusLogCallBack(Messages.getString("TorrentZipCheck.IncorrectFileOrderFound")); //$NON-NLS-1$
					}

				}
			}
		}

		// ***************************** RULE 3 *************************************
		// Directory marker files are only needed if they are empty directories.
		//
		// now that the files are sorted correctly, we can see if there are unneeded
		// directory files, by first finding directory files (these end in a '\' character ascii 92)
		// and then checking if the next file is a file in that found directory.
		// If we find this 2 entry pattern (directory followed by file in that directory)
		// then the directory entry should not be present and the torrentzip is incorrect.
		// return ExtraDirectoryEnteries if error is found.
		var error3 = false;
		for(var i = 0; i < zippedFiles.size() - 1; i++)
		{
			// check if this is a directory entry
			if(zippedFiles.get(i).Name.charAt(zippedFiles.get(i).Name.length() - 1) != '/')
				continue;

			// check if the next filename is shorter or equal to this filename.
			// if it is shorter or equal it cannot be a file in the directory.
			if(zippedFiles.get(i + 1).Name.length() <= zippedFiles.get(i).Name.length())
				continue;

			// check if the directory part of the two file enteries match
			// if they do we found an incorrect directory entry.
			var delete = true;
			for(var j = 0; j < zippedFiles.get(i).Name.length(); j++)
			{
				if(zippedFiles.get(i).Name.charAt(j) != zippedFiles.get(i + 1).Name.charAt(j))
				{
					delete = false;
					break;
				}
			}

			// we found an incorrect directory so remove it.
			if(delete)
			{
				zippedFiles.remove(i);
				tzStatus.add(TrrntZipStatus.ExtraDirectoryEntries);
				if(!error3 && StatusLogCallBack.isVerboseLogging())
				{
					error3 = true;
					StatusLogCallBack.StatusLogCallBack(Messages.getString("TorrentZipCheck.UnneededDirectoryRecordsFound")); //$NON-NLS-1$
				}

				i--;
			}
		}

		// check for repeat files
		var error4 = false;
		for(var i = 0; i < zippedFiles.size() - 1; i++)
		{
			if(zippedFiles.get(i).Name.equals(zippedFiles.get(i + 1).Name))
			{
				tzStatus.add(TrrntZipStatus.RepeatFilesFound);
				if(!error4 && StatusLogCallBack.isVerboseLogging())
				{
					error4 = true;
					StatusLogCallBack.StatusLogCallBack(Messages.getString("TorrentZipCheck.DuplicateFileEntriesFound")); //$NON-NLS-1$
				}
			}
		}

		return tzStatus;
	}

}

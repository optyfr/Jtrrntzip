package JTrrntzip;

import java.util.EnumSet;
import java.util.List;

public class TorrentZipCheck
{
    public static EnumSet<TrrntZipStatus> CheckZipFiles(List<ZippedFile> zippedFiles)
    {
        EnumSet<TrrntZipStatus> tzStatus = EnumSet.noneOf(TrrntZipStatus.class);


        // ***************************** RULE 1 *************************************
        // Directory separator should be a '/' a '\' is invalid and should be replaced with '/'
        //
        // check if any '\' = 92 need converted to '/' = 47
        // this needs done before the sort, so that the sort is correct.
        // return BadDirectorySeparator if errors found.
        boolean error1 = false;
        for(ZippedFile t : zippedFiles)
        {
            char[] bytes = t.Name.toCharArray();
            boolean fixDir = false;
            for (int j = 0; j < bytes.length; j++)
            {
                if (bytes[j] != 92) continue;
                fixDir = true;
                bytes[j] = (char)47;
                tzStatus.add(TrrntZipStatus.BadDirectorySeparator);
                if (!error1 && Program.VerboseLogging)
                {
                    error1 = true;
                    System.out.println("Incorrect directory separator found");
                }
            }
            if (fixDir) t.Name = new String(bytes);
        }


        // ***************************** RULE 2 *************************************
        // All Files in a torrentzip should be sorted with a lower case file compare.
        //
        // if needed sort the files correctly, and return Unsorted if errors found.
        boolean error2 = false;
        boolean thisSortFound = true;
        while (thisSortFound)
        {
            thisSortFound = false;
            for (int i = 0; i < zippedFiles.size() - 1; i++)
            {
                int c = TrrntZipStringCompare(zippedFiles.get(i).Name, zippedFiles.get(i+1).Name);
                if (c > 0)
                {
                    ZippedFile T = zippedFiles.get(i);
                    zippedFiles.set(i, zippedFiles.get(i + 1));
                    zippedFiles.set(i+1, T);

                    tzStatus.add(TrrntZipStatus.Unsorted);
                    thisSortFound = true;
                    if (!error2 && Program.VerboseLogging)
                    {
                        error2 = true;
                        System.out.println("Incorrect file order found");
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
        boolean error3 = false;
        for (int i = 0; i < zippedFiles.size() - 1; i++)
        {
            // check if this is a directory entry
            if (zippedFiles.get(i).Name.charAt(zippedFiles.get(i).Name.length() - 1) != 47)
                continue;

            // check if the next filename is shorter or equal to this filename.
            // if it is shorter or equal it cannot be a file in the directory.
            if (zippedFiles.get(i+1).Name.length() <= zippedFiles.get(i).Name.length())
                continue;

            // check if the directory part of the two file enteries match
            // if they do we found an incorrect directory entry.
            boolean delete = true;
            for (int j = 0; j < zippedFiles.get(i).Name.length(); j++)
            {
                if (zippedFiles.get(i).Name.charAt(j) != zippedFiles.get(i+1).Name.charAt(j))
                {
                    delete = false;
                    break;
                }
            }

            // we found an incorrect directory so remove it.
            if (delete)
            {
                zippedFiles.remove(i);
                tzStatus.add(TrrntZipStatus.ExtraDirectoryEnteries);
                if (!error3 && Program.VerboseLogging)
                {
                    error3 = true;
                    System.out.println("Un-needed directory records found");
                }

                i--;
            }
        }


        // check for repeat files
        boolean error4 = false;
        for (int i = 0; i < zippedFiles.size() - 1; i++)
        {
            if (zippedFiles.get(i).Name == zippedFiles.get(i+1).Name)
            {
                tzStatus.add(TrrntZipStatus.RepeatFilesFound);
                if (!error4 && Program.VerboseLogging)
                {
                    error4 = true;
                    System.out.println("Duplcate file enteries found");
                }
            }
        }

        return tzStatus;
    }



    // perform an ascii based lower case string file compare
    private static int TrrntZipStringCompare(String string1, String string2)
    {
        char[] bytes1 = string1.toCharArray();
        char[] bytes2 = string2.toCharArray();

        int pos1 = 0;
        int pos2 = 0;

        for (;;)
        {
            if (pos1 == bytes1.length)
                return ((pos2 == bytes2.length) ? 0 : -1);
            if (pos2 == bytes2.length)
                return 1;

            int byte1 = bytes1[pos1++];
            int byte2 = bytes2[pos2++];

            if (byte1 >= 65 && byte1 <= 90) byte1 += 0x20;
            if (byte2 >= 65 && byte2 <= 90) byte2 += 0x20;

            if (byte1 < byte2)
                return -1;
            if (byte1 > byte2)
                return 1;
        }
    }
}

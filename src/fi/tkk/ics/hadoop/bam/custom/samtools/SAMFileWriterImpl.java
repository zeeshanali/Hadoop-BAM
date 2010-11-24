// Copied because we have a different SAMRecord. Excludes sorting and sort
// order checking to reduce dependencies.
//
// Required because of BAMFileWriter.

/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package fi.tkk.ics.hadoop.bam.custom.samtools;

import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMTextHeaderCodec;

import net.sf.samtools.util.SortingCollection;

import java.io.StringWriter;

/**
 * Base class for implementing SAM writer with any underlying format.
 * Mostly this manages accumulation & sorting of SAMRecords when appropriate,
 * and produces the text version of the header, since that seems to be a popular item
 * in both text and binary file formats.
 */
public abstract class SAMFileWriterImpl implements SAMFileWriter
{
    private static int DEAFULT_MAX_RECORDS_IN_RAM = 500000;      
    private int maxRecordsInRam = DEAFULT_MAX_RECORDS_IN_RAM;
    private SAMFileHeader.SortOrder sortOrder;
    private SAMFileHeader header;
    private SortingCollection<SAMRecord> alignmentSorter;

    // If true, records passed to addAlignment are already in the order specified by sortOrder
    private boolean presorted;

    /**
     * When writing records that are not presorted, specify the number of records stored in RAM
     * before spilling to disk.  This method sets the default value for all SamFileWriterImpl
     * instances. Must be called before the constructor is called.
     * @param maxRecordsInRam
     */
    public static void setDefaultMaxRecordsInRam(final int maxRecordsInRam) {
    	DEAFULT_MAX_RECORDS_IN_RAM = maxRecordsInRam;	
    }
    
    /**
     * When writing records that are not presorted, this number determines the 
     * number of records stored in RAM before spilling to disk.
     * @return DEAFULT_MAX_RECORDS_IN_RAM 
     */
    public static int getDefaultMaxRecordsInRam() {
    	return DEAFULT_MAX_RECORDS_IN_RAM;	
    }
    
    
    /**
     * Must be called before calling setHeader().  SortOrder value in the header passed
     * to setHeader() is ignored.  If setSortOrder is not called, default is SortOrder.unsorted.
     */
    public void setSortOrder(final SAMFileHeader.SortOrder sortOrder, final boolean presorted) {
        if (header != null) {
            throw new IllegalStateException("Cannot call SAMFileWriterImpl.setSortOrder after setHeader for " +
                    getFilename());
        }
        this.sortOrder = sortOrder;
        this.presorted = presorted;
    }

    /**
     * Must be called after calling setHeader().
     */
    protected SAMFileHeader.SortOrder getSortOrder() {
        return this.sortOrder;
    }

    /**
     * When writing records that are not presorted, specify the number of records stored in RAM
     * before spilling to disk.  Must be called before setHeader().
     * @param maxRecordsInRam
     */
    void setMaxRecordsInRam(final int maxRecordsInRam) {
        if (this.header != null) {
            throw new IllegalStateException("setMaxRecordsInRam must be called before setHeader()");
        }
        this.maxRecordsInRam = maxRecordsInRam;
    }

    /**
     * Must be called before addAlignment.
     */
    public void setHeader(final SAMFileHeader header)
    {
        this.header = header;
        if (sortOrder == null) {
             sortOrder = SAMFileHeader.SortOrder.unsorted;
        }
        header.setSortOrder(sortOrder);
        final StringWriter headerTextBuffer = new StringWriter();
        new SAMTextHeaderCodec().encode(headerTextBuffer, header);
        final String headerText = headerTextBuffer.toString();

        writeHeader(headerText);
    }

    public SAMFileHeader getFileHeader() {
        return header;
    }

    public void addAlignment(final SAMRecord alignment)
    {
        if (sortOrder.equals(SAMFileHeader.SortOrder.unsorted)) {
            if (!header.getGroupOrder().equals(SAMFileHeader.GroupOrder.none)) {
                throw new UnsupportedOperationException("GroupOrder " + header.getGroupOrder() + " is not supported");
            }
            writeAlignment(alignment);
        } else if (presorted) {
            writeAlignment(alignment);
        } else {
            alignmentSorter.add(alignment);
        }
    }

    /**
     * Must be called or else file will likely be defective.
     */
    public final void close()
    {
        if (alignmentSorter != null) {
            for (final SAMRecord alignment : alignmentSorter) {
                writeAlignment(alignment);
            }
            alignmentSorter.cleanup();
        }
        finish();
    }

    /**
     * Writes the record to disk.  Sort order has been taken care of by the time
     * this method is called.
     * @param alignment
     */
    abstract protected void writeAlignment(SAMRecord alignment);

    /**
     * Write the header to disk.  Header object is available via getHeader().
     * @param textHeader for convenience if the implementation needs it.
     */
    abstract protected void writeHeader(String textHeader);

    /**
     * Do any required flushing here.
     */
    abstract protected void finish();

    /**
     * For producing error messages.
     * @return Output filename, or null if there isn't one.
     */
    abstract protected String getFilename();
}
package org.apache.iceberg.orc;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgument;
import org.apache.iceberg.Schema;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.expressions.Binder;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.hadoop.HadoopInputFile;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.mapping.MappingUtil;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.util.Pair;
import org.apache.orc.OrcFile;
import org.apache.orc.Reader;
import org.apache.orc.TypeDescription;

import java.io.IOException;
import java.util.function.Function;

class PxfOrcIterable<T> extends CloseableGroup implements CloseableIterable<T> {

    private final Configuration config;
    private final Schema schema;
    private final InputFile file;
    private final Long start;
    private final Long length;
    private final Function<TypeDescription, PxfOrcRowReader<?>> readerFunction;
    private final Expression filter;
    private final boolean caseSensitive;
    private final int recordsPerBatch;
    private NameMapping nameMapping;

    PxfOrcIterable(
            InputFile file,
            Configuration config,
            Schema schema,
            NameMapping nameMapping,
            Long start,
            Long length,
            Function<TypeDescription, PxfOrcRowReader<?>> readerFunction,
            boolean caseSensitive,
            Expression filter,
            int recordsPerBatch) {
        this.schema = schema;
        this.readerFunction = readerFunction;
        this.file = file;
        this.nameMapping = nameMapping;
        this.start = start;
        this.length = length;
        this.config = config;
        this.caseSensitive = caseSensitive;
        this.filter = (filter == Expressions.alwaysTrue()) ? null : filter;
        this.recordsPerBatch = recordsPerBatch;
    }

    @SuppressWarnings("unchecked")
    @Override
    public CloseableIterator<T> iterator() {
        Reader orcFileReader = newFileReader(file, config);
        addCloseable(orcFileReader);

        TypeDescription fileSchema = orcFileReader.getSchema();
        final TypeDescription readOrcSchema;
        if (ORCSchemaUtil.hasIds(fileSchema)) {
            readOrcSchema = ORCSchemaUtil.buildOrcProjection(schema, fileSchema);
        } else {
            if (nameMapping == null) {
                nameMapping = MappingUtil.create(schema);
            }
            TypeDescription typeWithIds = ORCSchemaUtil.applyNameMapping(fileSchema, nameMapping);
            readOrcSchema = ORCSchemaUtil.buildOrcProjection(schema, typeWithIds);
        }

        SearchArgument sarg = null;
        if (filter != null) {
            Expression boundFilter = Binder.bind(schema.asStruct(), filter, caseSensitive);
            sarg = PxfExpressionToSearchArgument.convert(boundFilter, readOrcSchema);
        }

        PxfVectorizedRowBatchIterator rowBatchIterator =
                newOrcIterator(file, readOrcSchema, start, length, orcFileReader, sarg, recordsPerBatch);

        return new OrcRowIterator<>(
                rowBatchIterator, (PxfOrcRowReader<T>) readerFunction.apply(readOrcSchema));
    }

    private static Reader newFileReader(InputFile file, Configuration config) {
        OrcFile.ReaderOptions readerOptions = OrcFile.readerOptions(config).useUTCTimestamp(true);
        if (file instanceof HadoopInputFile) {
            readerOptions.filesystem(((HadoopInputFile) file).getFileSystem());
        } else {
            readerOptions.filesystem(new FileIOFSUtil.InputFileSystem(file)).maxLength(file.getLength());
        }
        try {
            return OrcFile.createReader(new Path(file.location()), readerOptions);
        } catch (IOException ioe) {
            throw new RuntimeIOException(ioe, "Failed to open file: %s", file.location());
        }
    }

    private static PxfVectorizedRowBatchIterator newOrcIterator(
            InputFile file,
            TypeDescription readerSchema,
            Long start,
            Long length,
            Reader orcFileReader,
            SearchArgument sarg,
            int recordsPerBatch) {
        final Reader.Options options = orcFileReader.options();
        if (start != null) {
            options.range(start, length);
        }
        options.schema(readerSchema);
        options.searchArgument(sarg, new String[]{});

        try {
            return new PxfVectorizedRowBatchIterator(
                    file.location(), readerSchema, orcFileReader.rows(options), recordsPerBatch);
        } catch (IOException ioe) {
            throw new RuntimeIOException(ioe, "Failed to get ORC rows for file: %s", file.location());
        }
    }

    private static class OrcRowIterator<T> implements CloseableIterator<T> {

        private int nextRow;
        private VectorizedRowBatch current;
        private int currentBatchSize;

        private final PxfVectorizedRowBatchIterator batchIter;
        private final PxfOrcRowReader<T> reader;

        OrcRowIterator(PxfVectorizedRowBatchIterator batchIter, PxfOrcRowReader<T> reader) {
            this.batchIter = batchIter;
            this.reader = reader;
            current = null;
            nextRow = 0;
            currentBatchSize = 0;
        }

        @Override
        public boolean hasNext() {
            return (current != null && nextRow < currentBatchSize) || batchIter.hasNext();
        }

        @Override
        public T next() {
            if (current == null || nextRow >= currentBatchSize) {
                Pair<VectorizedRowBatch, Long> nextBatch = batchIter.next();
                current = nextBatch.first();
                currentBatchSize = current.size;
                nextRow = 0;
                this.reader.setBatchContext(nextBatch.second());
            }
            int rowId = current.isSelectedInUse() ? current.selected[nextRow] : nextRow;
            nextRow++;
            return this.reader.read(current, rowId);
        }

        @Override
        public void close() throws IOException {
            batchIter.close();
        }
    }
}

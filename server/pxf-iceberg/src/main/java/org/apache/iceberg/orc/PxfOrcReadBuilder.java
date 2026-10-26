package org.apache.iceberg.orc;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.iceberg.Schema;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.hadoop.HadoopInputFile;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.mapping.NameMapping;
import com.google.common.base.Preconditions;
import org.apache.orc.OrcConf;
import org.apache.orc.TypeDescription;

import java.util.function.Function;

/**
 * ORC ReadBuilder that uses {@code org.apache.hadoop.hive.ql} classes (from hive-storage-api)
 * instead of {@code org.apache.orc.storage.ql} shaded classes bundled by older iceberg-orc versions.
 * <p>
 * This avoids {@link NoSuchMethodError} when {@code orc-core} at runtime expects
 * {@code org.apache.hadoop.hive.ql.io.sarg.SearchArgument} but iceberg-orc was compiled against
 * an older orc-core that shaded those classes under {@code org.apache.orc.storage.ql}.
 */
public class PxfOrcReadBuilder {

    private final InputFile file;
    private final Configuration conf;
    private Schema schema = null;
    private Long start = null;
    private Long length = null;
    private Expression filter = null;
    private boolean caseSensitive = true;
    private NameMapping nameMapping = null;
    private Function<TypeDescription, PxfOrcRowReader<?>> readerFunc;
    private int recordsPerBatch = VectorizedRowBatch.DEFAULT_SIZE;

    public PxfOrcReadBuilder(InputFile file) {
        Preconditions.checkNotNull(file, "Input file cannot be null");
        this.file = file;
        if (file instanceof HadoopInputFile) {
            this.conf = new Configuration(((HadoopInputFile) file).getConf());
        } else {
            this.conf = new Configuration();
        }
        this.conf.setBoolean(OrcConf.FORCE_POSITIONAL_EVOLUTION.getHiveConfName(), false);
    }

    public PxfOrcReadBuilder split(long newStart, long newLength) {
        this.start = newStart;
        this.length = newLength;
        return this;
    }

    public PxfOrcReadBuilder project(Schema newSchema) {
        this.schema = newSchema;
        return this;
    }

    public PxfOrcReadBuilder caseSensitive(boolean newCaseSensitive) {
        OrcConf.IS_SCHEMA_EVOLUTION_CASE_SENSITIVE.setBoolean(this.conf, newCaseSensitive);
        this.caseSensitive = newCaseSensitive;
        return this;
    }

    public PxfOrcReadBuilder config(String property, String value) {
        conf.set(property, value);
        return this;
    }

    public PxfOrcReadBuilder createReaderFunc(Function<TypeDescription, PxfOrcRowReader<?>> readerFunction) {
        this.readerFunc = readerFunction;
        return this;
    }

    public PxfOrcReadBuilder filter(Expression newFilter) {
        this.filter = newFilter;
        return this;
    }

    public PxfOrcReadBuilder recordsPerBatch(int numRecordsPerBatch) {
        this.recordsPerBatch = numRecordsPerBatch;
        return this;
    }

    public PxfOrcReadBuilder withNameMapping(NameMapping newNameMapping) {
        this.nameMapping = newNameMapping;
        return this;
    }

    public <D> CloseableIterable<D> build() {
        Preconditions.checkNotNull(schema, "Schema is required");
        return new PxfOrcIterable<>(
                file,
                conf,
                schema,
                nameMapping,
                start,
                length,
                readerFunc,
                caseSensitive,
                filter,
                recordsPerBatch);
    }
}

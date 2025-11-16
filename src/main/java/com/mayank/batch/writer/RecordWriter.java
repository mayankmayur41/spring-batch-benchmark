package com.mayank.batch.writer;

import com.mayank.batch.model.Record;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
public class RecordWriter implements ItemWriter<Record> {

    @Override
    public void write(Chunk<? extends Record> chunk) throws Exception {
        for (Record item : chunk.getItems()) {
            System.out.println("Writing record: " + item.getId());
        }
    }
}

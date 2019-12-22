import datetime as dt
import base64
import math
import json
import uuid
import hashlib

import boto3

client = boto3.client(
    "kinesis",
    region_name="us-east-1",
    aws_access_key_id='',
    aws_secret_access_key='',
    endpoint_url="http://kinesis:4567",
    use_ssl=False,
)

def get_records(stream_name, start_time: dt.datetime):
    shard_ids = [shard['ShardId'] for shard in client.describe_stream(StreamName=stream_name)['StreamDescription']['Shards']]

    def get_shard_iterator(stream_name, shard_id):
        return client.get_shard_iterator(
            StreamName=stream_name,
            ShardId=shard_id,
            ShardIteratorType='AT_TIMESTAMP',
            Timestamp=start_time
        )['ShardIterator']

    shard_iterators = [(shard_id, get_shard_iterator(stream_name, shard_id)) for shard_id in shard_ids]

    records = [record for shard_id, shard_iterator in shard_iterators for record in client.get_records(ShardIterator=shard_iterator, Limit=10)['Records']]

    return [record['Data'].decode() for record in records]
    
def put_record(record, size=5):
    def split_record(whole_record, size):
        record_id = str(uuid.uuid4())
        timestamp = int(dt.datetime.now().timestamp()*1000)
        piece_count = math.ceil(len(whole_record) / size)
        for i in range(piece_count):
            piece = whole_record[i*size:min((i+1)*size, len(whole_record))]
            yield {"record_id": record_id, "order": i, "count": piece_count, "record": piece, "timestamp": timestamp}

    for piece in split_record(record, size):
        partition_key = hashlib.md5("{}.{}".format(piece['record_id'], piece['count']).encode()).hexdigest()
        piece_data = json.dumps(piece)
        response = client.put_record(
            StreamName="test-stream",
            Data=piece_data,
            PartitionKey=partition_key,
        )
        print(response)

sample_record = "aaaaabbbbbccccc"
start_time = dt.datetime.now()

put_record(sample_record, 5)
print(get_records('test-stream', start_time=start_time))
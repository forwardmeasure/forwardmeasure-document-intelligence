# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
# https://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
import json,copy,hashlib,argparse
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
def output(path,value):
 text=json.dumps(value,indent=2)+'\n'
 if args.check:
  if path.read_text()!=text:raise SystemExit('Contract drift: '+str(path))
 else:path.write_text(text)
from pathlib import Path
R=Path(__file__).resolve().parents[1]
D=R/'forwardmeasure-document-intelligence-api-specifications/src/main/resources/META-INF/openapi'
original=json.loads((D/'docling-serve-1.37.0.openapi.json').read_text())
schema=copy.deepcopy(original)
def normalize(value):
 if isinstance(value,dict):
  value.pop('default',None)
  value.pop('discriminator',None)
  if 'const' in value:
   value.pop('const')
  if 'prefixItems' in value:
   items=value.pop('prefixItems');types={item.get('type') for item in items};value['items']={'type':next(iter(types))} if len(types)==1 else {};
   if types=={'integer'}:value['items']['format']='int64'
  for item in value.values():normalize(item)
 elif isinstance(value,list):
  for item in value:normalize(item)
normalize(schema)
renames={'/health':'health','/ready':'readiness','/version':'versions','/v1/capabilities':'capabilities','/v1/convert/source':'convertSource','/v1/convert/file':'convertFile','/v1/convert/source/async':'submitSource','/v1/convert/file/async':'submitFile','/v1/result/{task_id}':'fetchResult','/v1/status/poll/{task_id}':'pollTask','/v1/chunk/hierarchical/source':'chunkHierarchicalSource','/v1/chunk/hybrid/source':'chunkHybridSource'}
for path,operations in schema['paths'].items():
 for method,operation in operations.items():
  if path in renames:operation['operationId']=renames[path]
  if path.startswith('/v1/convert/') and not path.endswith('/async') or path.startswith('/v1/chunk/') and not path.endswith('/async') or path=='/v1/result/{task_id}':
   operation['responses']['200']['content']={'application/json':{'schema':{'type':'string','format':'binary'}},'application/zip':{'schema':{'type':'string','format':'binary'}}}
output(D/'docling-serve-1.37.0.codegen.json',schema)
metadata={'image':'quay.io/docling-project/docling-serve-cpu@sha256:6eda9c524992147c1b0b06a1a9be4894bd68b1f43c86d92c32f996c4c60d18a3','captureDate':'2026-10-14','method':'create_app().openapi() in the pinned image, network disabled, lifespan not started','packages':json.loads((D/'docling-runtime-1.37.0.json').read_text())['packages'],'paths':len(original['paths']),'schemas':len(original['components']['schemas']),'files':{name:hashlib.sha256((D/name).read_bytes()).hexdigest() for name in ['docling-serve-1.37.0.openapi.json','docling-serve-1.37.0.codegen.json']},'normalizations':['Remove Python defaults and const/discriminator annotations for Java union generation; runtime validates union variants.','Represent homogeneous tuples as arrays of their original element type (integers use int64); mixed tuples use Object arrays. Retain minItems/maxItems.','Rename adopted operationIds; preserve every upstream REST path and schema.','Return conversion, chunk and task result bodies as opaque files to preserve native JSON and ZIP bytes.']}
output(D/'contract-provenance.json',metadata)

# Object storage

The attacker is any client of the download endpoint or `get_object_content`, or an agent uploading
objects.

- Download (`storage` package: the resource resolver and HTTP request handler): path traversal or
  encoded separators in object keys, and fetching objects by key or id without any ownership check.
- Upload: can a crafted file name or content type overwrite another object, escape the bucket or
  table, or get served back as active content (HTML/SVG) to the UI?
- Size limits: can an upload or `get_object_content` exhaust memory?
- MinIO configuration: credentials, bucket policy, and whether the strategy trusts keys from the
  caller.

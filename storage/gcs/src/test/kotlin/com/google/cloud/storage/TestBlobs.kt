package com.google.cloud.storage

/** Builds a [Blob] for a fake [Storage]; [Blob]'s constructors are package-private. */
fun testBlob(storage: Storage, blobId: BlobId): Blob = BlobInfo.newBuilder(blobId).build().asBlob(storage)

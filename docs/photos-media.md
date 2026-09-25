# Photos and media API

PDR-07 stores private family images and dated photo records for children. The API base is `/api`. Send `Authorization: Bearer <access-token>` on every request; the current caller must be a member of `{familyId}`. A missing family membership, a family resource belonging elsewhere, or an inaccessible child returns a safe 404. All current family roles can use these routes. No public file URLs, image transforms, or automatic cleanup are available.

Set `MEDIA_STORAGE_DIRECTORY` to a private directory outside the repository and web root. `MEDIA_MAX_BYTES` defaults to 10485760 (10 MiB); `MEDIA_MAX_REQUEST_BYTES` defaults to 11534336 to allow multipart overhead. The server creates the directory as needed. Upload bytes are stored there; PostgreSQL holds metadata and associations. Back up both together. The default directory is `${user.home}/.beehome/media`.

When running the Compose `app` profile, `/var/lib/beehome/media` is backed by the `media-data` named volume and owned by the application user. The `.env.example` path applies when running the application directly on the host.

## Routes

| Method | Path after `/api` | Success | Purpose |
| --- | --- | --- | --- |
| POST | `/families/{familyId}/media` | 201 | Upload one private image. |
| GET | `/families/{familyId}/media` | 200 | List family media. |
| GET | `/families/{familyId}/media/{mediaId}/content` | 200 | Read private image bytes. |
| DELETE | `/families/{familyId}/media/{mediaId}` | 204 | Delete unused media and bytes. |
| POST | `/families/{familyId}/children/{childId}/photo-records` | 201 | Create a dated record. |
| GET | `/families/{familyId}/children/{childId}/photo-records` | 200 | List dated records. |
| GET | `/families/{familyId}/children/{childId}/photo-records/{photoRecordId}` | 200 | Read one record. |
| PUT | `/families/{familyId}/children/{childId}/photo-records/{photoRecordId}` | 200 | Replace all editable fields. |
| DELETE | `/families/{familyId}/children/{childId}/photo-records/{photoRecordId}` | 204 | Delete record and links; keep media. |

All path IDs are UUIDs. `childId` identifies a family member of type `CHILD`. Create, replace, and delete require that child to be active. JSON calls use `Content-Type: application/json`. Upload uses `multipart/form-data` with one required part named `file`. The multipart filename and declared content type are untrusted; the server derives MIME type from the bytes. Accepted formats are JPEG, PNG, and WebP. Empty, invalid, spoofed, and oversized uploads fail. Before decoding, the server limits each image or animation frame to 8192 pixels per dimension and the total across all frames to 16,777,216 pixels. WebP canvases must also fit these dimension and pixel limits; animations are limited to 100 frames. These fixed limits apply independently of the configurable byte limit and return 400 `MEDIA_TOO_LARGE`. Every frame must decode successfully. WebP validation uses the TwelveMonkeys ImageIO decoder plus container and payload checks; file signatures alone are insufficient. Storage keys, original filenames, and local paths are absent from responses.

## Media

Example upload: `POST /api/families/0d20eb0d-f32c-4be1-ae88-1922cd763891/media` with a multipart `file` part containing image bytes.

201 response:

```json
{"id":"5481374b-c772-4c7e-a726-7f08326bd6a1","type":"IMAGE","mimeType":"image/png","sizeBytes":248631,"createdAt":"2026-09-23T15:00:00Z"}
```

Fields are required and non-null. `type` is currently always `IMAGE`; `sizeBytes` is the actual positive byte count. Store `id` for later attachment. Each upload creates a new asset, even if the same bytes are retried.

`GET /families/{familyId}/media` accepts `unattached` (`true` or `false`, default `false`), `page` (default 0), and `size` (default 20, range 1–100). Page numbers must be nonnegative. Items sort by `createdAt DESC, id DESC`. The response is `{"items":[<media response>],"page":0,"size":20,"hasNext":false}`. `unattached=true` selects assets with no photo record link and no book cover reference. Retain the asset ID and use this filter to find uploads left unattached after an interrupted client flow.

`GET /families/{familyId}/media/{mediaId}/content` returns image bytes with validated `Content-Type`, `Content-Disposition: inline`, and `Cache-Control: private, no-store`. The client should use an authenticated request and handle the response as a blob. An interrupted transfer should be discarded and retried; it does not change metadata. A storage read failure returns `MEDIA_UPLOAD_FAILED` when it occurs before streaming starts. A connection or storage failure after headers are sent may terminate the stream; retry the GET.

`DELETE /families/{familyId}/media/{mediaId}` returns an empty 204. It returns `MEDIA_IN_USE` while any photo record or book cover references the asset. Deleting a photo record does not remove the asset. If file deletion fails, the metadata row remains and the response is an error. A database failure after successful file deletion can leave a row whose bytes are missing; recover it from a coordinated backup or remove the stale metadata after investigation. There is no background repair job.

## Photo records

Create and replace use the same complete JSON body:

```json
{"date":"2026-09-21","description":"  First bike ride  ","mediaIds":["5481374b-c772-4c7e-a726-7f08326bd6a1"]}
```

`date` is a required ISO `yyyy-MM-dd` date. `description` is optional and nullable, trimmed, and limited to 2000 characters after trimming; blank becomes null. `mediaIds` is required and contains 1–20 unique UUIDs in display order. Every asset must exist in the same family and have type `IMAGE`. Uploaded assets can appear in multiple records. The caller identity supplies the creator; it cannot be passed in JSON. PUT replaces the date, description, and entire ordered media list in one transaction. Validate all IDs before changing a record. PUT with the same body can be retried after a lost response; the media order and content are replaced again.

201 create or 200 read/replace response:

```json
{"id":"8bfd9350-df22-48ec-b69b-f023aa6eae15","childId":"a799e711-5aca-465c-b677-8b9394b8bd94","date":"2026-09-21","description":"First bike ride","media":[{"id":"5481374b-c772-4c7e-a726-7f08326bd6a1","type":"IMAGE","position":0}],"createdAt":"2026-09-23T15:00:00Z","updatedAt":"2026-09-23T15:00:00Z"}
```

`description` may be null. `media` is nonempty and ordered by zero-based `position`. All other response fields are required and non-null. To display images, fetch each ID through the private content route; no direct URL is returned.

List accepts optional inclusive `from` and `to` ISO dates, `page` (default 0), and `size` (default 20, range 1–100). Either bound can be omitted; `from > to` is invalid. The response has the same `items`, `page`, `size`, and `hasNext` shape as media listing. Records sort by `date DESC, createdAt DESC, id DESC`. Empty lists return `items: []` and `hasNext: false`. GET of one record verifies the complete family, child, and record path. DELETE returns an empty 204 and retains its media assets. Reuse `unattached=true` to find them afterward.

## Errors and client flow

Errors use `application/problem+json` with `status`, localized `detail`, and stable `code`. Set `Accept-Language` to `en`, `pt`, or `es`; branch on `code`. Field and date validation errors use 400 `VALIDATION_ERROR`. Image validation uses 400 `MEDIA_INVALID_TYPE` or `MEDIA_TOO_LARGE`. Exceeding the multipart file or request byte limit also returns 400 `MEDIA_TOO_LARGE`, including failures before controller selection. Resize or recompress the image before retrying a size error; replace or re-encode invalid content before retrying a type error. A missing or cross-family media resource uses 404 `MEDIA_NOT_FOUND`; a referenced asset uses 409 `MEDIA_IN_USE`; storage failure uses 500 `MEDIA_UPLOAD_FAILED`. A missing record uses 404 `PHOTO_RECORD_NOT_FOUND`; a missing, cross-family, or non-image attachment uses 400 `PHOTO_RECORD_INVALID_MEDIA`. Family and child safe-not-found responses retain their existing family/member codes. Missing authentication uses 401 `UNAUTHENTICATED`. There is no photo-specific role restriction.

A normal client uploads each image, saves returned media IDs, then creates the record. On an upload timeout, list media and inspect unattached assets before deciding whether to upload again; duplicate uploads remain possible. On a record create timeout, query the child history before retrying POST because POST can create another record. On an update timeout, fetch the record and retry PUT if needed. Keep access tokens in the existing authentication flow. Existing request rate limits apply. Unsupported flows include video/audio, public links, image editing, thumbnails, album sharing, and automatic orphan cleanup.

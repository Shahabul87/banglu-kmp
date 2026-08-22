# Microsoft Store identity — বাংলু টাইপার (Banglu Typer)

Assigned by Partner Center when the product was created (2026-08-21). A Store
MSIX **must** declare exactly these values in its `AppxManifest.xml`; anything
else is rejected at upload. None of this is secret — every published package
carries it in the clear, and the Store ID is a public URL component.

| Field | Value |
|---|---|
| `Package/Identity/Name` | `BangluTyper.BangluTyper` |
| `Package/Identity/Publisher` | `CN=B71AC99C-C3DF-48A2-B8A6-957E15042785` |
| `Package/Properties/PublisherDisplayName` | `Banglu Typer` |
| Package Family Name (PFN) | `BangluTyper.BangluTyper_zxkv5b2y5hwhy` |
| Store ID | `9NVJGRJDRJGK` |

Package SID (rarely needed; used for service-to-app auth scenarios we do not
have): `S-1-15-2-144004385-3591701984-1864319313-3628071711-3066836994-3971565943-4191494737`

## Notes

- The `Publisher` string is an identity, not a certificate we hold. Microsoft
  re-signs Store packages with its own certificate at ingestion, which is the
  entire reason this app targets the Store rather than buying an Authenticode
  certificate (Store policy 10.2.9's direct-download route would still require
  one).
- To SIDELOAD a build for testing, the self-signed certificate's subject must
  match `Package/Identity/Publisher` exactly, or `Add-AppxPackage` refuses the
  package.
- Store deep link and web URL only exist once the product is live.

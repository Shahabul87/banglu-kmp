# Banglu 1.5.95 (2132) — S152

## bn-BD (Play Console "What's new")
সাজেশন বারে নীল চিপের নিচের ইংরেজি লেখা এখন বড় ও স্পষ্ট। courier লিখলেই কুরিয়ার, cash → ক্যাশ, bank → ব্যাংক — সঠিক ইংরেজি শব্দের সঠিক বাংলা উচ্চারণ, ইংরেজি শব্দটাও সাজেশনে।

## en-US
The roman hint under the primary suggestion chip is bigger and brighter. courier → কুরিয়ার (the tester's own example), cash → ক্যাশ, bank → ব্যাংক — correct English words get their correct Bengali pronunciation, with the English word kept in the suggestions.

## Internal
- Chip hint: scaledSp(9)/subText → scaledSp(11)/white-88% (renders only on
  the highlighted first chip).
- EnglishDirectData: কুরিয়ার(courier), ক্যাশ gains the "cash" alias;
  shorthand chat defaults cash → ক্যাশ, bank → ব্যাংক (চাষ/বাঁক strip twins
  pinned). Probe confirmed delivery/parcel/order/office/doctor/hospital/
  mobile/recharge/balance already correct — pinned as regression wall.
- Pins: S152TesterEnglishJvmTest. All five gradle walls green.

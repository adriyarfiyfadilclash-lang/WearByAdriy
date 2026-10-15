// Deletes the calling user's photos and account. Rows are removed by ON DELETE CASCADE.
// Runs server-side only; the service-role key never reaches the app.
import { createClient } from "npm:@supabase/supabase-js@2.45.4";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method not allowed" }, 405);

  const auth = req.headers.get("Authorization") ?? "";
  const token = auth.replace(/^Bearer\s+/i, "");
  if (!token) return json({ error: "missing token" }, 401);

  const url = Deno.env.get("SUPABASE_URL")!;
  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: userData, error: userError } = await admin.auth.getUser(token);
  if (userError || !userData?.user) return json({ error: "invalid session" }, 401);
  const uid = userData.user.id;

  // Remove every photo in the user's folder (paged).
  for (;;) {
    const { data: files, error } = await admin.storage.from("wardrobe").list(uid, { limit: 100 });
    if (error) return json({ error: "storage list failed" }, 500);
    if (!files || files.length === 0) break;
    const { error: rmError } = await admin.storage.from("wardrobe").remove(files.map((f) => `${uid}/${f.name}`));
    if (rmError) return json({ error: "storage delete failed" }, 500);
    if (files.length < 100) break;
  }

  const { error: delError } = await admin.auth.admin.deleteUser(uid);
  if (delError) return json({ error: "account delete failed" }, 500);
  return json({ deleted: true }, 200);
});

function json(body: unknown, status: number) {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
}

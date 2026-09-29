const error = () => Object.assign(new Error("This provider needs an authorized content API or license."), { code: "AUTHORIZATION_REQUIRED" });

export const descriptor = Object.freeze({
  id: "tfpdl",
  name: "TFPDL",
  mediaTypes: ["movie", "tv"],
  enabled: false,
  priority: 100
});

export async function search() {
  throw error();
}

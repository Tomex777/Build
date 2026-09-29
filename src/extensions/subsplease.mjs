const error = () => Object.assign(new Error("This provider needs an authorized content API or license."), { code: "AUTHORIZATION_REQUIRED" });

export const descriptor = Object.freeze({
  id: "subsplease",
  name: "SubsPlease",
  mediaTypes: ["anime"],
  enabled: false,
  priority: 100
});

export async function search() {
  throw error();
}

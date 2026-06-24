const departmentNames: Record<string, string> = {
  cse: '컴퓨터학부',
  media: '글로벌미디어학부',
  mediamba: '미디어경영학',
};

export function departmentCodeToName(code: string | null | undefined) {
  if (!code) {
    return '';
  }

  return departmentNames[code] ?? code;
}

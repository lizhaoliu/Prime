//! Minimal Wavefront OBJ loader.
//!
//! The legacy `ContentLoader` took a `MainGui` in its constructor and logged
//! straight to a Swing text area. This loader has zero UI dependencies: it is a
//! pure function from a path (plus a material id and optional transform) to a
//! list of triangles, returning a typed error.

use crate::geometry::Triangle;
use crate::math::Vec3;
use crate::{Float, MaterialId};
use std::fs::File;
use std::io::{BufRead, BufReader};
use std::path::Path;

#[derive(Debug, thiserror::Error)]
pub enum ObjError {
    #[error("failed to read OBJ file {path}: {source}")]
    Io {
        path: String,
        #[source]
        source: std::io::Error,
    },
    #[error("malformed OBJ at line {line}: {reason}")]
    Parse { line: usize, reason: String },
}

/// A uniform-scale-then-translate transform applied to loaded vertices.
#[derive(Clone, Copy, Debug)]
pub struct Transform {
    pub scale: Float,
    pub translate: Vec3,
}

impl Default for Transform {
    fn default() -> Self {
        Transform {
            scale: 1.0,
            translate: Vec3::ZERO,
        }
    }
}

impl Transform {
    #[inline]
    fn point(&self, p: Vec3) -> Vec3 {
        p * self.scale + self.translate
    }
}

/// Load all faces of an OBJ file as triangles, assigning every triangle the
/// given material. Smooth normals are used when the file provides them.
pub fn load(
    path: impl AsRef<Path>,
    material: MaterialId,
    transform: Transform,
) -> Result<Vec<Triangle>, ObjError> {
    load_filtered(path, None, material, transform)
}

/// Like [`load`], but if `group` is `Some(name)` only faces belonging to the
/// matching `g`/`o` group are loaded. This lets a multi-object OBJ be placed as
/// several scene objects with different materials.
pub fn load_filtered(
    path: impl AsRef<Path>,
    group: Option<&str>,
    material: MaterialId,
    transform: Transform,
) -> Result<Vec<Triangle>, ObjError> {
    let path = path.as_ref();
    let file = File::open(path).map_err(|source| ObjError::Io {
        path: path.display().to_string(),
        source,
    })?;
    let reader = BufReader::new(file);

    let mut positions: Vec<Vec3> = Vec::new();
    let mut normals: Vec<Vec3> = Vec::new();
    let mut triangles: Vec<Triangle> = Vec::new();
    let mut current_group: Option<String> = None;

    for (line_no, line) in reader.lines().enumerate() {
        let line = line.map_err(|source| ObjError::Io {
            path: path.display().to_string(),
            source,
        })?;
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }

        let mut tokens = line.split_whitespace();
        match tokens.next() {
            Some("v") => positions.push(transform.point(parse_vec3(tokens, line_no)?)),
            Some("vn") => normals.push(parse_vec3(tokens, line_no)?),
            // Track the current group/object name.
            Some("g") | Some("o") => current_group = tokens.next().map(|s| s.to_string()),
            Some("f") => {
                // Skip faces outside the requested group (vertices/normals still
                // accumulate globally, so indices stay valid).
                if group.is_some() && group != current_group.as_deref() {
                    continue;
                }
                let verts: Vec<FaceVert> = tokens
                    .map(|t| FaceVert::parse(t, positions.len(), normals.len(), line_no))
                    .collect::<Result<_, _>>()?;
                if verts.len() < 3 {
                    return Err(ObjError::Parse {
                        line: line_no + 1,
                        reason: "face with fewer than 3 vertices".into(),
                    });
                }
                // Fan-triangulate the (possibly n-gon) face.
                for i in 1..verts.len() - 1 {
                    let tri = make_triangle(
                        &positions,
                        &normals,
                        verts[0],
                        verts[i],
                        verts[i + 1],
                        material,
                    )?;
                    triangles.push(tri);
                }
            }
            // Smoothing, materials, texcoords: ignored for now.
            _ => {}
        }
    }

    Ok(triangles)
}

#[derive(Clone, Copy)]
struct FaceVert {
    pos: usize,
    normal: Option<usize>,
}

impl FaceVert {
    /// Parse a `v`, `v/t`, `v//n`, or `v/t/n` token, resolving 1-based and
    /// negative (relative) indices into 0-based ones.
    fn parse(
        token: &str,
        n_pos: usize,
        n_norm: usize,
        line_no: usize,
    ) -> Result<FaceVert, ObjError> {
        let mut parts = token.split('/');
        let pos = resolve_index(parts.next().unwrap_or(""), n_pos, line_no)?;
        let _tex = parts.next(); // texture coords ignored
        let normal = match parts.next() {
            Some(s) if !s.is_empty() => Some(resolve_index(s, n_norm, line_no)?),
            _ => None,
        };
        Ok(FaceVert { pos, normal })
    }
}

fn resolve_index(s: &str, count: usize, line_no: usize) -> Result<usize, ObjError> {
    let idx: i64 = s.parse().map_err(|_| ObjError::Parse {
        line: line_no + 1,
        reason: format!("invalid index '{s}'"),
    })?;
    let resolved = if idx > 0 {
        idx - 1
    } else if idx < 0 {
        count as i64 + idx
    } else {
        return Err(ObjError::Parse {
            line: line_no + 1,
            reason: "index 0 is not valid in OBJ".into(),
        });
    };
    if resolved < 0 || resolved as usize >= count {
        return Err(ObjError::Parse {
            line: line_no + 1,
            reason: format!("index {idx} out of range"),
        });
    }
    Ok(resolved as usize)
}

fn make_triangle(
    positions: &[Vec3],
    normals: &[Vec3],
    a: FaceVert,
    b: FaceVert,
    c: FaceVert,
    material: MaterialId,
) -> Result<Triangle, ObjError> {
    let tri = Triangle::new(
        positions[a.pos],
        positions[b.pos],
        positions[c.pos],
        material,
    );
    match (a.normal, b.normal, c.normal) {
        (Some(na), Some(nb), Some(nc)) => {
            Ok(tri.with_normals([normals[na], normals[nb], normals[nc]]))
        }
        _ => Ok(tri),
    }
}

fn parse_vec3<'a>(
    mut tokens: impl Iterator<Item = &'a str>,
    line_no: usize,
) -> Result<Vec3, ObjError> {
    let mut next = || -> Result<Float, ObjError> {
        tokens
            .next()
            .ok_or_else(|| ObjError::Parse {
                line: line_no + 1,
                reason: "expected 3 floats".into(),
            })?
            .parse::<Float>()
            .map_err(|_| ObjError::Parse {
                line: line_no + 1,
                reason: "invalid float".into(),
            })
    };
    Ok(Vec3::new(next()?, next()?, next()?))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn parses_a_quad_into_two_triangles() {
        let tmp = tempfile_path();
        {
            let mut f = File::create(&tmp).unwrap();
            writeln!(f, "# a quad").unwrap();
            writeln!(f, "v 0 0 0").unwrap();
            writeln!(f, "v 1 0 0").unwrap();
            writeln!(f, "v 1 1 0").unwrap();
            writeln!(f, "v 0 1 0").unwrap();
            writeln!(f, "vn 0 0 1").unwrap();
            writeln!(f, "f 1//1 2//1 3//1 4//1").unwrap();
        }
        let tris = load(&tmp, 0, Transform::default()).unwrap();
        assert_eq!(tris.len(), 2);
        assert!(tris[0].normals.is_some());
        std::fs::remove_file(&tmp).ok();
    }

    #[test]
    fn group_filter_loads_only_matching_faces() {
        let tmp = tempfile_path();
        {
            let mut f = File::create(&tmp).unwrap();
            for line in ["v 0 0 0", "v 1 0 0", "v 0 1 0", "v 0 0 1"] {
                writeln!(f, "{line}").unwrap();
            }
            writeln!(f, "g alpha").unwrap();
            writeln!(f, "f 1 2 3").unwrap();
            writeln!(f, "g beta").unwrap();
            writeln!(f, "f 1 2 4").unwrap();
            writeln!(f, "f 2 3 4").unwrap();
        }
        let all = load(&tmp, 0, Transform::default()).unwrap();
        let alpha = load_filtered(&tmp, Some("alpha"), 0, Transform::default()).unwrap();
        let beta = load_filtered(&tmp, Some("beta"), 0, Transform::default()).unwrap();
        assert_eq!(all.len(), 3);
        assert_eq!(alpha.len(), 1);
        assert_eq!(beta.len(), 2);
        std::fs::remove_file(&tmp).ok();
    }

    #[test]
    fn negative_indices_resolve() {
        let tmp = tempfile_path();
        {
            let mut f = File::create(&tmp).unwrap();
            writeln!(f, "v 0 0 0").unwrap();
            writeln!(f, "v 1 0 0").unwrap();
            writeln!(f, "v 0 1 0").unwrap();
            writeln!(f, "f -3 -2 -1").unwrap();
        }
        let tris = load(&tmp, 0, Transform::default()).unwrap();
        assert_eq!(tris.len(), 1);
        assert_eq!(tris[0].v0, Vec3::ZERO);
        std::fs::remove_file(&tmp).ok();
    }

    fn tempfile_path() -> String {
        let mut p = std::env::temp_dir();
        // Unique-ish name without extra deps.
        let n = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        p.push(format!("prime_obj_test_{n}.obj"));
        p.display().to_string()
    }
}
